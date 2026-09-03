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
    note: b.note ?? null,
    totalPrice: b.totalPrice ?? null,
    paymentMethod: b.paymentMethod ?? null,
  };
  orders.push(order);
  if (orders.length > MAX_ORDERS) orders = orders.slice(-MAX_ORDERS);
  persist();
  broadcast({ type: 'new', order });
  console.log('Don moi:', order.id, '| items:', order.items);
  res.json({ ok: true, id: order.id });
});

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
  ws.on('message', () => {}); // may D3 khong can gui gi len
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
