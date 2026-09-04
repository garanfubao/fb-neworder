/**
 * Fubao Order Server
 * - Nhan don tu n8n:            POST /orders   (header X-Api-Key)
 * - Day realtime xuong may D3:  WebSocket /ws?key=API_KEY
 *
 * Luu don ra file JSON (con song sau khi Render restart neu co gan disk).
 */
const express = require('express');
const http = require('http');
const { WebSocketServer } = require('ws');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const API_KEY = process.env.API_KEY || 'doi-key-nay-di';
const MAX_ORDERS = parseInt(process.env.MAX_ORDERS || '100', 10);
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'orders.json');

// Khi bam "Xong": bao n8n gui tin Messenger cho khach.
// - N8N_DELIVERY_WEBHOOK_URL: URL webhook cua node n8n (bo trong thi tinh nang tat).
// - DELIVERY_MESSAGE: noi dung tin nhan (co the doi tren Render, khong can sua code).
const N8N_DELIVERY_WEBHOOK_URL = process.env.N8N_DELIVERY_WEBHOOK_URL || '';
const DELIVERY_MESSAGE =
  process.env.DELIVERY_MESSAGE ||
  'Bếp đang trên đường giao hàng, mình xuống sảnh nhận cơm giúp em nha';

// ---------- Luu tru don hang ----------
let orders = [];
try {
  if (fs.existsSync(DATA_FILE)) {
    const parsed = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    if (Array.isArray(parsed)) orders = parsed;
  }
} catch (e) {
  console.error('Khong doc duoc file luu don:', e.message);
}

let saveTimer = null;
function persist() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      fs.writeFileSync(DATA_FILE, JSON.stringify(orders.slice(-MAX_ORDERS)));
    } catch (e) {
      console.error('Ghi file luu don loi:', e.message);
    }
  }, 500);
}

// ---------- HTTP ----------
const app = express();
app.use(express.json({ limit: '256kb' }));

app.get('/healthz', (_req, res) => res.status(200).send('ok'));
app.get('/', (_req, res) =>
  res.status(200).send('Fubao Order Server dang chay. So don dang giu: ' + orders.length)
);

function keyOk(req) {
  const k = req.get('X-Api-Key') || req.query.key;
  return typeof k === 'string' && k.length > 0 && k === API_KEY;
}

// n8n goi vao day (node "Notify Desktop App")
app.post('/orders', (req, res) => {
  if (!keyOk(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
  const b = req.body || {};
  const order = {
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 7),
    receivedAt: new Date().toISOString(),
    customerName: b.customerName ?? null,
    phone: b.phone ?? null,
    address: b.address ?? null,
    items: b.items ?? null,
    note: b.note ?? b.notes ?? null,
    totalPrice: b.totalPrice ?? null,
    paymentMethod: b.paymentMethod ?? null,
    // PSID Messenger cua khach -> de bam "Xong" con nhan tin bao giao hang.
    psid: b.psid ?? b.senderId ?? null,
  };

  // Khach sua mon -> chatbot ban don lan 2. Neu con don CU CUNG KHACH chua bam Xong
  // thi thay the (khong tao them the moi). Uu tien khop psid, khong co thi khop SDT.
  const dupIdx = orders.findIndex((o) => sameCustomer(o, order));
  let replacedId = null;
  if (dupIdx !== -1) {
    replacedId = orders[dupIdx].id;
    orders.splice(dupIdx, 1);
    broadcast({ type: 'removed', id: replacedId });
  }

  orders.push(order);
  if (orders.length > MAX_ORDERS) orders = orders.slice(-MAX_ORDERS);
  persist();
  broadcast({ type: 'new', order });
  if (replacedId) console.log('Don sua: thay', replacedId, '->', order.id, '| items:', order.items);
  else console.log('Don moi:', order.id, '| items:', order.items);
  res.json({ ok: true, id: order.id, replaced: replacedId });
});

// Cung mot khach? Uu tien psid; chua co psid thi dung SDT lam khoa du phong.
function sameCustomer(a, b) {
  if (a.psid && b.psid) return a.psid === b.psid;
  if (a.phone && b.phone) return a.phone === b.phone;
  return false;
}

// Bao n8n gui tin Messenger "dang giao hang" cho khach cua don vua xong.
async function notifyDelivery(order) {
  if (!N8N_DELIVERY_WEBHOOK_URL) return; // chua bat tinh nang
  if (!order || !order.psid) {
    console.warn('Bo qua bao giao hang: don thieu psid ->', order && order.id);
    return;
  }
  try {
    const r = await fetch(N8N_DELIVERY_WEBHOOK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Api-Key': API_KEY },
      body: JSON.stringify({
        psid: order.psid,
        message: DELIVERY_MESSAGE,
        orderId: order.id,
        customerName: order.customerName,
        items: order.items,
      }),
    });
    if (!r.ok) console.error('Webhook n8n tra loi HTTP', r.status);
    else console.log('Da bao n8n gui tin giao hang | don', order.id, '| psid', order.psid);
  } catch (e) {
    console.error('Goi webhook n8n loi:', e.message);
  }
}

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

function broadcast(msg) {
  const data = JSON.stringify(msg);
  wss.clients.forEach((c) => {
    if (c.readyState === 1) {
      try { c.send(data); } catch (_) {}
    }
  });
}

wss.on('connection', (ws, req) => {
  let key = null;
  try {
    key = new URL(req.url, 'http://x').searchParams.get('key');
  } catch (_) {}
  if (key !== API_KEY) {
    ws.close(4001, 'unauthorized');
    return;
  }
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });
  ws.on('message', (raw) => {
    let m;
    try { m = JSON.parse(raw.toString()); } catch (_) { return; }
    // May D3 bam "Xong" -> xoa don khoi server + bao cac may khac xoa theo
    if (m && m.type === 'done' && m.id) {
      const doneOrder = orders.find((o) => o.id === m.id);
      const before = orders.length;
      orders = orders.filter((o) => o.id !== m.id);
      if (orders.length !== before) {
        persist();
        broadcast({ type: 'removed', id: m.id });
        console.log('Don xong, da xoa:', m.id);
        // Gui 1 lan duy nhat: chi may thuc su xoa duoc don moi ban webhook.
        notifyDelivery(doneOrder);
      }
    }
  });
  // Gui 50 don gan nhat de bang hien ngay khi mo app (khong keu chuong)
  ws.send(JSON.stringify({ type: 'snapshot', orders: orders.slice(-50) }));
  console.log('May D3 da ket noi. Tong ket noi:', wss.clients.size);
});

// Giu ket noi song / phat hien ket noi chet
setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) return ws.terminate();
    ws.isAlive = false;
    try { ws.ping(); } catch (_) {}
  });
}, 30000);

server.listen(PORT, () => console.log('Fubao Order Server nghe cong ' + PORT));
