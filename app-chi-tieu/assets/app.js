/**
 * SỔ CHI TIÊU - ứng dụng điện thoại (phiên bản 1.1)
 * Chuyển từ bản Google Trang tính (phiên bản 8):
 *  - Trang ngày: các khoản trong ngày, ngày hôm nay nổi bật hơn các ngày khác
 *  - Tổng hợp: tình hình thu chi theo ngày, tuần, tháng, năm; biểu đồ tròn
 *    phân bổ theo hạng mục và biểu đồ cột đôi thu chi theo thời gian
 *  - Cài đặt: màu giao diện, xuất file Excel hoặc JSON, nhập dữ liệu
 * Mọi khoản nằm chung một kho nên không thể đếm trùng, cũng không cần dồn
 * ngày cũ vào sổ cái hay hẹn giờ lúc 0h.
 */
'use strict';

const HANG_MUC = [
  'Ăn uống', 'Đi lại', 'Mua sắm', 'Giải trí', 'Học tập',
  'Sức khỏe', 'Hóa đơn', 'Nhà ở', 'Lương', 'Được cho', 'Khác'
];

/** Màu gán cho hạng mục theo đúng thứ tự trong mảng HANG_MUC */
const MAU_HANG_MUC = [
  '#4e79a7', '#f28e2b', '#e15759', '#76b7b2', '#59a14f', '#edc948',
  '#b07aa1', '#ff9da7', '#9c755f', '#8cd17d', '#86bcb6', '#d37295',
  '#a0cbe8', '#ffbe7d', '#b6992d', '#79706e'
];

const BIEU_TUONG = {
  'Ăn uống': '🍜', 'Đi lại': '🛵', 'Mua sắm': '🛍️', 'Giải trí': '🎮',
  'Học tập': '📚', 'Sức khỏe': '💊', 'Hóa đơn': '🧾', 'Nhà ở': '🏠',
  'Lương': '💰', 'Được cho': '🎁', 'Khác': '📦', 'Còn lại': '🗂️'
};

/** Các màu giao diện chọn được trong Cài đặt */
const MAU_GIAO_DIEN = [
  { ten: 'Xanh đậm', ma: '#1f3864' },
  { ten: 'Xanh dương', ma: '#1b5eab' },
  { ten: 'Xanh ngọc', ma: '#00695c' },
  { ten: 'Xanh lá', ma: '#2e6b4f' },
  { ten: 'Tím', ma: '#5b3a9e' },
  { ten: 'Hồng', ma: '#b0306d' },
  { ten: 'Cam đất', ma: '#a8521c' },
  { ten: 'Xám than', ma: '#37474f' }
];

/** Màu cột thu và chi trong biểu đồ cột đôi */
const MAU_COT = { Thu: '#1e7a3c', Chi: '#b03030' };
const MAU_CON_LAI = '#c3c8d1';
const MAU_LUOI = '#e3e7ee';
const MAU_TRUC = '#c9cfd8';
const MAU_CHU_MO = '#5f6673';
const MAU_CHU = '#1b1f24';

const THU_TRONG_TUAN = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư',
                        'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
const THU_NGAN = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

const MAU_TEN_NGAY = /^\d{4}-\d{2}-\d{2}$/;
const MAU_GIO = /^\d{2}:\d{2}$/;
const KHOA_LUU = 'so_chi_tieu';
const SO_LAT_TOI_DA = 5;       // biểu đồ tròn: nhiều hơn số lát này thì gộp phần nhỏ vào "Còn lại"

function mauHM_(ten) {
  let i = HANG_MUC.indexOf(ten);
  if (i < 0) {
    let tong = 0;
    const s = String(ten);
    for (let k = 0; k < s.length; k++) tong += s.charCodeAt(k);
    i = tong;
  }
  return MAU_HANG_MUC[i % MAU_HANG_MUC.length];
}

function bieuTuong_(ten) { return BIEU_TUONG[ten] || '🏷️'; }

function kenhMau_(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** Trộn hai màu, tyLe là phần của màu thứ hai */
function tronMau_(a, b, tyLe) {
  const x = kenhMau_(a), y = kenhMau_(b);
  return '#' + x.map(function (c, i) {
    return ('0' + Math.round(c + (y[i] - c) * tyLe).toString(16)).slice(-2);
  }).join('');
}


/* ========== NGÀY GIỜ ========== */

function hai_(n) { return (n < 10 ? '0' : '') + n; }

function chuoiNgay_(d) {
  return d.getFullYear() + '-' + hai_(d.getMonth() + 1) + '-' + hai_(d.getDate());
}

function tuChuoiNgay_(s) {
  const p = s.split('-').map(Number);
  return new Date(p[0], p[1] - 1, p[2]);
}

function dinhDang_(d) {
  return hai_(d.getDate()) + '/' + hai_(d.getMonth() + 1) + '/' + d.getFullYear();
}

function ngayThang_(d) { return hai_(d.getDate()) + '/' + hai_(d.getMonth() + 1); }

function tenNgay_(d) {
  return THU_TRONG_TUAN[d.getDay()] + ', ' + dinhDang_(d);
}

function congNgay_(d, n) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

function soNgayGiua_(a, b) {
  return Math.round((b - a) / 86400000);
}

function homNay_() { return chuoiNgay_(new Date()); }

function gioHienTai_() {
  const d = new Date();
  return hai_(d.getHours()) + ':' + hai_(d.getMinutes());
}

function layGio_(gio) {
  return MAU_GIO.test(gio) ? Number(gio.slice(0, 2)) : 0;
}


/* ========== TIỀN ========== */

function nhomSo_(n) {
  return String(Math.round(Math.abs(n))).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

function tien_(n) { return (n < 0 ? '−' : '') + nhomSo_(n) + ' đ'; }

/** Số dư mang dấu cộng hoặc trừ như định dạng TIEN_CO_DAU */
function tienCoDau_(n) {
  if (n > 0) return '+' + nhomSo_(n) + ' đ';
  if (n < 0) return '−' + nhomSo_(n) + ' đ';
  return '0 đ';
}

/** Số gọn cho trục biểu đồ: 500k, 1,5tr, 2 tỷ */
function tienGon_(n) {
  const gon = function (x, donVi) {
    return String(Math.round(x * 10) / 10).replace('.', ',') + donVi;
  };
  if (n >= 1e9) return gon(n / 1e9, ' tỷ');
  if (n >= 1e6) return gon(n / 1e6, 'tr');
  if (n >= 1e3) return gon(n / 1e3, 'k');
  return String(Math.round(n));
}

function phanTram_(v, tong) {
  const p = tong ? v / tong * 100 : 0;
  return (p > 0 && p < 10 ? String(Math.round(p * 10) / 10).replace('.', ',') : String(Math.round(p))) + '%';
}

/** Đọc số tiền từ ô bảng tính: số, hoặc chữ như "50.000 đ", "50,000" */
function docSoTien_(v) {
  if (typeof v === 'number') return Math.round(v);
  let s = String(v == null ? '' : v).replace(/[^\d.,-]/g, '');
  const am = s.indexOf('-') === 0;
  s = s.replace(/-/g, '');
  const thapPhan = s.match(/[.,](\d{1,2})$/);
  const so = thapPhan
    ? Number(s.slice(0, thapPhan.index).replace(/[.,]/g, '') + '.' + thapPhan[1])
    : Number(s.replace(/[.,]/g, ''));
  if (!isFinite(so)) return 0;
  return Math.round(am ? -so : so);
}

function boDau_(s) {
  return String(s).normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
}


/* ========== KHO DỮ LIỆU ========== */

let duLieu = { caiDat: { mau: MAU_GIAO_DIEN[0].ma, anSo: false }, giaoDich: [] };

function coCauNoi_() {
  return typeof window.Android !== 'undefined' && window.Android !== null;
}

function docKho_() {
  if (coCauNoi_()) {
    try { return window.Android.docDuLieu(); } catch (e) { return null; }
  }
  try { return localStorage.getItem(KHOA_LUU); } catch (e) { return null; }
}

function ghiKho_(chuoi) {
  if (coCauNoi_()) {
    try { return window.Android.ghiDuLieu(chuoi) === true; } catch (e) { return false; }
  }
  try { localStorage.setItem(KHOA_LUU, chuoi); return true; } catch (e) { return false; }
}

function taoId_() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

function chuanHoa_(g) {
  if (!g || typeof g !== 'object') return null;
  const ngay = String(g.ngay || '').slice(0, 10);
  const hm = String(g.hm == null ? '' : g.hm).trim();
  if (!MAU_TEN_NGAY.test(ngay) || !hm) return null;
  return {
    id: String(g.id || taoId_()),
    ngay: ngay,
    gio: MAU_GIO.test(g.gio) ? g.gio : '',
    loai: g.loai === 'Thu' ? 'Thu' : 'Chi',
    hm: hm,
    tien: Math.max(0, Math.round(Number(g.tien) || 0)),
    ghiChu: String(g.ghiChu == null ? '' : g.ghiChu).trim(),
    tao: Number(g.tao) || Date.now()
  };
}

/** Làm sạch một danh sách, đồng thời bảo đảm mã các khoản không trùng nhau */
function chuanHoaDanhSach_(ds) {
  const daCo = {};
  const ket = [];
  (ds || []).forEach(function (g) {
    const c = chuanHoa_(g);
    if (!c) return;
    if (daCo[c.id]) c.id = taoId_();
    daCo[c.id] = true;
    ket.push(c);
  });
  return ket;
}

function napDuLieu_() {
  const chuoi = docKho_();
  if (!chuoi) return;
  try {
    const obj = JSON.parse(chuoi);
    duLieu.giaoDich = chuanHoaDanhSach_(Array.isArray(obj) ? obj : obj.giaoDich);
    const cd = obj && obj.caiDat;
    if (cd && MAU_GIAO_DIEN.some(function (m) { return m.ma === cd.mau; })) duLieu.caiDat.mau = cd.mau;
    if (cd) duLieu.caiDat.anSo = cd.anSo === true;
  } catch (e) {
    thongBao_('Không đọc được dữ liệu đã lưu.');
  }
}

function luuDuLieu_() {
  const ok = ghiKho_(JSON.stringify({
    phienBan: 1, caiDat: duLieu.caiDat, giaoDich: duLieu.giaoDich
  }));
  if (!ok) thongBao_('Không lưu được dữ liệu vào máy!');
}


/* ========== TÍNH TOÁN ========== */

function tongHop_(ds) {
  let thu = 0, chi = 0;
  const theoHM = {};
  ds.forEach(function (g) {
    const laThu = g.loai === 'Thu';
    if (laThu) thu += g.tien; else chi += g.tien;
    if (!theoHM[g.hm]) theoHM[g.hm] = { thu: 0, chi: 0 };
    if (laThu) theoHM[g.hm].thu += g.tien; else theoHM[g.hm].chi += g.tien;
  });
  return { thu: thu, chi: chi, du: thu - chi, theoHM: theoHM };
}

/** Khoản không ghi giờ đứng đầu, còn lại theo giờ rồi theo lúc ghi */
function theoGio_(a, b) {
  if (a.gio !== b.gio) return a.gio < b.gio ? -1 : 1;
  return a.tao - b.tao;
}

function sapTheoNgay_(ds) {
  return ds.slice().sort(function (a, b) {
    return a.ngay < b.ngay ? -1 : a.ngay > b.ngay ? 1 : theoGio_(a, b);
  });
}

function trongKhoang_(bd, kt) {
  return duLieu.giaoDich.filter(function (g) { return g.ngay >= bd && g.ngay <= kt; });
}

/** Tính khoảng ngày cần xem */
function tinhKhoang_(ky, moc) {
  const d = new Date(moc.getFullYear(), moc.getMonth(), moc.getDate());
  let bd, kt;

  if (ky === 'Tuần') {
    const lui = (d.getDay() + 6) % 7;            // đưa về thứ Hai
    bd = congNgay_(d, -lui);
    kt = congNgay_(bd, 6);
  } else if (ky === 'Tháng') {
    bd = new Date(d.getFullYear(), d.getMonth(), 1);
    kt = new Date(d.getFullYear(), d.getMonth() + 1, 0);
  } else if (ky === 'Năm') {
    bd = new Date(d.getFullYear(), 0, 1);
    kt = new Date(d.getFullYear(), 11, 31);
  } else {
    bd = d; kt = d;
  }

  return { bd: chuoiNgay_(bd), kt: chuoiNgay_(kt), dBD: bd, dKT: kt };
}

/** Lùi mốc về kỳ trước (buoc âm) hoặc tiến tới kỳ sau */
function dichMoc_(ky, moc, buoc) {
  if (ky === 'Tuần') return congNgay_(moc, 7 * buoc);
  if (ky === 'Tháng') return new Date(moc.getFullYear(), moc.getMonth() + buoc, 1);
  if (ky === 'Năm') return new Date(moc.getFullYear() + buoc, 0, 1);
  return congNgay_(moc, buoc);
}

/** Tên kỳ đang xem ("Hôm nay", "Tuần trước", "Tháng 8/2026"...) cùng dòng phụ ghi rõ ngày */
function tenKy_(ky, kh) {
  const homNay = new Date();
  const lech = function (buoc) {
    return tinhKhoang_(ky, dichMoc_(ky, homNay, buoc)).bd === kh.bd;
  };
  if (ky === 'Tuần') {
    const phu = ngayThang_(kh.dBD) + ' – ' + dinhDang_(kh.dKT);
    if (lech(0)) return ['Tuần này', phu];
    if (lech(-1)) return ['Tuần trước', phu];
    return ['Tuần ' + ngayThang_(kh.dBD) + ' – ' + ngayThang_(kh.dKT), String(kh.dKT.getFullYear())];
  }
  if (ky === 'Tháng') {
    const ten = 'Tháng ' + (kh.dBD.getMonth() + 1) + '/' + kh.dBD.getFullYear();
    if (lech(0)) return ['Tháng này', ten];
    if (lech(-1)) return ['Tháng trước', ten];
    return [ten, ''];
  }
  if (ky === 'Năm') {
    const ten = 'Năm ' + kh.dBD.getFullYear();
    if (lech(0)) return ['Năm nay', ten];
    if (lech(-1)) return ['Năm trước', ten];
    return [ten, ''];
  }
  if (lech(0)) return ['Hôm nay', tenNgay_(kh.dBD)];
  if (lech(-1)) return ['Hôm qua', tenNgay_(kh.dBD)];
  return [tenNgay_(kh.dBD), ''];
}

/**
 * Kỳ để so sánh: kỳ liền trước. Kỳ đang diễn ra (chứa hôm nay) thì so với
 * cùng kỳ, tức là chỉ lấy đúng số ngày đã qua của kỳ trước.
 */
function kyTruoc_(ky, kh) {
  const truoc = tinhKhoang_(ky, dichMoc_(ky, kh.dBD, -1));
  const homNay = homNay_();
  const ten = {
    'Ngày': kh.bd === homNay ? 'hôm qua' : 'hôm trước',
    'Tuần': 'tuần trước', 'Tháng': 'tháng trước', 'Năm': 'năm trước'
  }[ky];
  if (ky === 'Ngày' || homNay < kh.bd || homNay > kh.kt) return { bd: truoc.bd, kt: truoc.kt, ten: ten };

  let kt;
  if (ky === 'Năm') {
    const d = new Date();
    kt = chuoiNgay_(new Date(truoc.dBD.getFullYear(), d.getMonth(), d.getDate()));
  } else {
    kt = chuoiNgay_(congNgay_(truoc.dBD, soNgayGiua_(kh.dBD, tuChuoiNgay_(homNay))));
  }
  if (kt > truoc.kt) kt = truoc.kt;
  return { bd: truoc.bd, kt: kt, ten: 'cùng kỳ ' + ten };
}

/** Các mốc trên trục ngang của biểu đồ cột */
function mocBieuDo_(ky, dBD, dKT) {
  if (ky === 'Tuần') {
    const ngan = [], dai = [], ngay = [];
    for (let i = 0; i < 7; i++) {
      const d = congNgay_(dBD, i);
      ngan.push(THU_NGAN[d.getDay()] + '\n' + ngayThang_(d));
      dai.push(tenNgay_(d));
      ngay.push(chuoiNgay_(d));
    }
    return {
      ngan: ngan, dai: dai, donVi: 'ngày',
      chiSo: function (g) { return ngay.indexOf(g.ngay); }
    };
  }

  if (ky === 'Tháng') {
    const soNgay = dKT.getDate();
    const ngan = [], dai = [];
    for (let i = 1; i <= soNgay; i++) {
      ngan.push(hai_(i));
      dai.push(tenNgay_(new Date(dBD.getFullYear(), dBD.getMonth(), i)));
    }
    return {
      ngan: ngan, dai: dai, donVi: 'ngày',
      chiSo: function (g) { return Number(g.ngay.slice(8, 10)) - 1; }
    };
  }

  if (ky === 'Năm') {
    const ngan = [], dai = [];
    for (let i = 1; i <= 12; i++) {
      ngan.push('T' + i);
      dai.push('Tháng ' + i + '/' + dBD.getFullYear());
    }
    return {
      ngan: ngan, dai: dai, donVi: 'tháng',
      chiSo: function (g) { return Number(g.ngay.slice(5, 7)) - 1; }
    };
  }

  const ngan = [], dai = [];
  for (let i = 0; i < 24; i++) {
    ngan.push(i + 'h');
    dai.push('Từ ' + i + 'h đến ' + (i + 1) + 'h');
  }
  return {
    ngan: ngan, dai: dai, donVi: 'giờ',
    chiSo: function (g) { return layGio_(g.gio); }
  };
}


/* ========== GIAO DIỆN CHUNG ========== */

function $(id) { return document.getElementById(id); }

function tao_(the, lop, chu) {
  const e = document.createElement(the);
  if (lop) e.className = lop;
  if (chu != null) e.textContent = chu;
  return e;
}

const SVG_NS = 'http://www.w3.org/2000/svg';
function svg_(ten, thuocTinh, cha) {
  const e = document.createElementNS(SVG_NS, ten);
  for (const k in thuocTinh) e.setAttribute(k, thuocTinh[k]);
  if (cha) cha.appendChild(e);
  return e;
}

const trangThai = {
  trang: 'trangNgay',
  ngay: homNay_(),
  theoHomNay: true,           // đang xem hôm nay thì qua 0h tự sang trang ngày mới
  homNayDaKiem: homNay_(),
  ky: 'Ngày',                 // mặc định: tổng thu chi hôm nay
  moc: new Date(),
  cheDo: 'tron',              // 'tron': biểu đồ phân bổ, 'cot': biểu đồ cột thu chi
  loaiXem: 'Chi',
  hmChon: '',
  bangMo: null,
  dangSua: null,
  loaiNhap: 'Chi',
  hmNhap: ''
};

function apDungMau_(ma) {
  const goc = document.documentElement.style;
  goc.setProperty('--dam', ma);
  goc.setProperty('--dam-toi', tronMau_(ma, '#000000', 0.2));
  goc.setProperty('--dam-vua', tronMau_(ma, '#ffffff', 0.72));
  goc.setProperty('--dam-nhat', tronMau_(ma, '#ffffff', 0.87));
  if (coCauNoi_() && window.Android.doiMauThanh) {
    try { window.Android.doiMauThanh(ma); } catch (e) { /* máy không đổi được màu thanh trạng thái thì thôi */ }
  }
}

function chuyenTrang_(ten) {
  trangThai.trang = ten;
  document.querySelectorAll('main > .trang').forEach(function (s) {
    s.hidden = s.id !== ten;
  });
  document.querySelectorAll('.thanh-duoi button').forEach(function (b) {
    if (b.dataset.trang === ten) b.setAttribute('aria-current', 'page');
    else b.removeAttribute('aria-current');
  });
  $('nutThem').hidden = ten !== 'trangNgay';
  window.scrollTo(0, 0);
  veTrangHienTai_();
}

function veTrangHienTai_() {
  if (trangThai.trang === 'trangNgay') veTrangNgay_();
  else if (trangThai.trang === 'trangTongHop') veTongHop_();
  else veCaiDat_();
}

let henTatThongBao = null;

/** Thông báo ngắn ở cuối màn hình, có thể kèm một nút (ví dụ Hoàn tác) */
function thongBao_(chu, nut) {
  const hop = $('thongBao');
  $('tbChu').textContent = chu;
  const b = $('tbNut');
  b.hidden = !nut;
  b.onclick = null;
  if (nut) {
    b.textContent = nut.nhan;
    b.onclick = function () { anThongBao_(); nut.lam(); };
  }
  hop.hidden = false;
  document.body.classList.add('co-thong-bao');
  clearTimeout(henTatThongBao);
  henTatThongBao = setTimeout(anThongBao_, nut ? 6000 : 3000);
}

function anThongBao_() {
  $('thongBao').hidden = true;
  document.body.classList.remove('co-thong-bao');
}

/** Mở một bảng trượt từ dưới lên, mỗi lúc chỉ một bảng */
function moBang_(id) {
  if (trangThai.bangMo && trangThai.bangMo !== id) $(trangThai.bangMo).hidden = true;
  trangThai.bangMo = id;
  $('lopPhu').hidden = false;
  $(id).hidden = false;
  $(id).scrollTop = 0;
}

function dongBang_() {
  if (trangThai.bangMo) $(trangThai.bangMo).hidden = true;
  trangThai.bangMo = null;
  trangThai.dangSua = null;
  $('lopPhu').hidden = true;
  if (document.activeElement) document.activeElement.blur();
}


/* ========== TRANG NGÀY ========== */

function taoDongGiaoDich_(g, stt) {
  const li = tao_('li');
  const b = tao_('button', 'dong dong-luoi');
  b.type = 'button';
  b.dataset.id = g.id;

  b.appendChild(tao_('span', 'stt', String(stt)));
  const cotGio = tao_('span', 'cot-gio');
  cotGio.appendChild(tao_('span', 'gio', g.gio || '--:--'));
  cotGio.appendChild(tao_('span', 'nhan-loai ' + (g.loai === 'Thu' ? 'thu' : 'chi'), g.loai));
  b.appendChild(cotGio);

  const nd = tao_('span', 'noi-dung');
  const hm = tao_('span', 'hm');
  const cham = tao_('i', 'cham');
  cham.style.background = mauHM_(g.hm);
  hm.appendChild(cham);
  hm.appendChild(tao_('span', null, g.hm));
  nd.appendChild(hm);
  if (g.ghiChu) nd.appendChild(tao_('span', 'ghi-chu', g.ghiChu));
  b.appendChild(nd);

  b.appendChild(tao_('span', 'so-tien', tien_(g.tien)));
  b.setAttribute('aria-label', 'Khoản ' + stt + ': ' + g.loai + ' ' + g.hm + ' ' + tien_(g.tien)
    + (g.gio ? ' lúc ' + g.gio : '') + (g.ghiChu ? ', ' + g.ghiChu : '') + '. Bấm để sửa.');
  li.appendChild(b);
  return li;
}

function veTrangNgay_() {
  const laHomNay = trangThai.ngay === homNay_();
  $('nhanNgay').textContent = tenNgay_(tuChuoiNgay_(trangThai.ngay));
  $('chonNgay').value = trangThai.ngay;
  $('nhanHomNay').hidden = !laHomNay;
  $('daiNgay').classList.toggle('ngay-khac', !laHomNay);

  const ds = duLieu.giaoDich
    .filter(function (g) { return g.ngay === trangThai.ngay; })
    .sort(theoGio_);

  const ol = $('dsNgay');
  ol.textContent = '';
  ds.forEach(function (g, i) { ol.appendChild(taoDongGiaoDich_(g, i + 1)); });
  $('trongNgay').hidden = ds.length > 0;
}

function denNgay_(ngay) {
  trangThai.ngay = ngay;
  trangThai.theoHomNay = ngay === homNay_();
  if (trangThai.trang !== 'trangNgay') chuyenTrang_('trangNgay');
  else veTrangNgay_();
}

/** Qua 0h mà vẫn đang xem hôm nay thì tự mở trang ngày mới */
function kiemTraQuaNgay_() {
  const homNay = homNay_();
  if (homNay === trangThai.homNayDaKiem) return;
  trangThai.homNayDaKiem = homNay;
  if (trangThai.theoHomNay) trangThai.ngay = homNay;
  veTrangHienTai_();
}


/* ========== BẢNG NHẬP MỘT KHOẢN ========== */

function veLuoiHangMuc_() {
  const luoi = $('luoiHangMuc');
  luoi.textContent = '';
  const ds = HANG_MUC.slice();
  // Khoản cũ mang hạng mục ngoài danh sách thì vẫn hiện để giữ nguyên khi sửa
  if (trangThai.hmNhap && ds.indexOf(trangThai.hmNhap) < 0) ds.push(trangThai.hmNhap);

  ds.forEach(function (ten) {
    const mau = mauHM_(ten);
    const chon = ten === trangThai.hmNhap;
    const b = tao_('button');
    b.type = 'button';
    b.dataset.hm = ten;
    b.setAttribute('role', 'radio');
    b.setAttribute('aria-checked', String(chon));
    const cham = tao_('span', 'cham');
    cham.style.background = mau;
    b.appendChild(cham);
    b.appendChild(tao_('span', null, ten));
    if (chon) {
      b.style.borderColor = mau;
      b.style.background = mau + '26';
    }
    luoi.appendChild(b);
  });
}

function chonLoaiNhap_(loai) {
  trangThai.loaiNhap = loai;
  $('chonLoai').querySelectorAll('button').forEach(function (b) {
    b.setAttribute('role', 'radio');
    b.setAttribute('aria-checked', String(b.dataset.loai === loai));
  });
}

function moBangNhap_(g) {
  $('tieuDeNhap').textContent = g ? 'Sửa khoản' : 'Thêm khoản mới';
  chonLoaiNhap_(g ? g.loai : 'Chi');
  trangThai.hmNhap = g ? g.hm : '';
  veLuoiHangMuc_();
  $('oSoTien').value = g ? nhomSo_(g.tien) : '';
  $('oNgayNhap').value = g ? g.ngay : trangThai.ngay;
  // Giống trang tính: ghi khoản mới thì tự điền giờ hiện tại
  $('oGioNhap').value = g ? g.gio : gioHienTai_();
  $('oGhiChu').value = g ? g.ghiChu : '';
  $('loiNhap').hidden = true;
  $('nutXoa').hidden = !g;

  moBang_('bangNhap');
  trangThai.dangSua = g ? g.id : null;
  if (!g) $('oSoTien').focus();
}

function baoLoiNhap_(chu) {
  const p = $('loiNhap');
  p.textContent = chu;
  p.hidden = false;
}

function luuBangNhap_() {
  const tien = Number($('oSoTien').value.replace(/\D/g, '')) || 0;
  if (tien <= 0) { baoLoiNhap_('Hãy nhập số tiền.'); $('oSoTien').focus(); return; }
  if (!trangThai.hmNhap) { baoLoiNhap_('Hãy chọn một hạng mục.'); return; }

  const moi = {
    ngay: MAU_TEN_NGAY.test($('oNgayNhap').value) ? $('oNgayNhap').value : trangThai.ngay,
    gio: MAU_GIO.test($('oGioNhap').value) ? $('oGioNhap').value : '',
    loai: trangThai.loaiNhap,
    hm: trangThai.hmNhap,
    tien: tien,
    ghiChu: $('oGhiChu').value.trim()
  };

  const dangSua = trangThai.dangSua;
  if (dangSua) {
    const g = duLieu.giaoDich.find(function (x) { return x.id === dangSua; });
    if (g) Object.assign(g, moi);
  } else {
    moi.id = taoId_();
    moi.tao = Date.now();
    duLieu.giaoDich.push(moi);
  }
  luuDuLieu_();
  dongBang_();

  // Ghi cho ngày khác thì mở luôn trang ngày đó để thấy khoản vừa ghi
  if (trangThai.trang === 'trangNgay' && moi.ngay !== trangThai.ngay) denNgay_(moi.ngay);
  else veTrangHienTai_();

  thongBao_(dangSua ? 'Đã sửa khoản.'
    : 'Đã ghi: ' + moi.loai.toLowerCase() + ' ' + tien_(moi.tien) + ', ' + moi.hm + '.');
}

function xoaKhoanDangSua_() {
  const id = trangThai.dangSua;
  const viTri = duLieu.giaoDich.findIndex(function (x) { return x.id === id; });
  if (viTri < 0) return;
  const g = duLieu.giaoDich.splice(viTri, 1)[0];
  luuDuLieu_();
  dongBang_();
  veTrangHienTai_();
  thongBao_('Đã xóa ' + g.hm + ' ' + tien_(g.tien) + '.', {
    nhan: 'Hoàn tác',
    lam: function () {
      duLieu.giaoDich.push(g);
      luuDuLieu_();
      veTrangHienTai_();
    }
  });
}

/** Ô số tiền tự chèn dấu chấm ngăn cách hàng nghìn */
function dinhDangOTien_() {
  const o = $('oSoTien');
  const so = o.value.replace(/\D/g, '').replace(/^0+/, '').slice(0, 13);
  o.value = so ? nhomSo_(Number(so)) : '';
}


/* ========== TRANG TỔNG HỢP ========== */

/** Số tiền trên trang tổng hợp, che đi khi bấm con mắt */
function tienTH_(n) { return duLieu.caiDat.anSo ? '••••••' : tien_(n); }

function veTongHop_() {
  const ky = trangThai.ky;
  const kh = tinhKhoang_(ky, trangThai.moc);
  if (ky === 'Tuần') trangThai.moc = kh.dBD;

  const ten = tenKy_(ky, kh);
  $('nhanKy').textContent = ten[0];
  $('phuKy').textContent = ten[1];
  $('phuKy').hidden = !ten[1];
  $('kySau').disabled = kh.kt >= homNay_();

  const ds = trongKhoang_(kh.bd, kh.kt);
  const t = tongHop_(ds);
  const an = duLieu.caiDat.anSo;

  $('nutAnSo').setAttribute('aria-pressed', String(an));
  $('soChi').textContent = tienTH_(t.chi);
  $('soThu').textContent = tienTH_(t.thu);
  document.querySelectorAll('.o-tong').forEach(function (o) {
    o.setAttribute('aria-pressed', String(o.dataset.loai === trangThai.loaiXem));
  });
  const du = $('soDu');
  du.textContent = an ? '••••••' : tienCoDau_(t.du);
  du.className = an ? '' : t.du > 0 ? 'duong' : t.du < 0 ? 'am' : '';

  veSoSanh_(ky, kh, t);

  document.querySelectorAll('#chonCheDo button').forEach(function (b) {
    b.setAttribute('aria-checked', String(b.dataset.cheDo === trangThai.cheDo));
  });
  $('khungTron').hidden = trangThai.cheDo !== 'tron';
  $('khungCot').hidden = trangThai.cheDo !== 'cot';

  // Các hạng mục của loại đang xem, lớn trước nhỏ sau
  const layThu = trangThai.loaiXem === 'Thu';
  const dong = Object.keys(t.theoHM)
    .map(function (k) {
      return { ten: k, giaTri: layThu ? t.theoHM[k].thu : t.theoHM[k].chi, mau: mauHM_(k) };
    })
    .filter(function (x) { return x.giaTri > 0; })
    .sort(function (a, b) { return b.giaTri - a.giaTri; });

  if (trangThai.cheDo === 'tron') {
    veBieuDoTron_(dong, layThu);
  } else {
    const moc = mocBieuDo_(ky, kh.dBD, kh.dKT);
    const thu = new Array(moc.ngan.length).fill(0);
    const chi = new Array(moc.ngan.length).fill(0);
    ds.forEach(function (g) {
      const i = moc.chiSo(g);
      if (i < 0 || i >= moc.ngan.length) return;
      if (g.loai === 'Thu') thu[i] += g.tien; else chi[i] += g.tien;
    });
    $('tieuDeCot').textContent = 'Thu và chi theo ' + moc.donVi;
    veBieuDoCot_(moc, thu, chi);
  }

  veDsHangMuc_(dong, layThu);
}

function veSoSanh_(ky, kh, t) {
  const truoc = kyTruoc_(ky, kh);
  const tTruoc = tongHop_(trongKhoang_(truoc.bd, truoc.kt));
  const layThu = trangThai.loaiXem === 'Thu';
  const nay = layThu ? t.thu : t.chi;
  const cu = layThu ? tTruoc.thu : tTruoc.chi;
  const chu = $('chuSoSanh');
  chu.textContent = '';

  if (!nay && !cu) {
    chu.textContent = 'Chưa có khoản ' + (layThu ? 'thu' : 'chi') + ' nào, cả kỳ này lẫn ' + truoc.ten + '.';
    return;
  }
  if (nay === cu) {
    chu.textContent = (layThu ? 'Thu nhập' : 'Chi tiêu') + ' bằng với ' + truoc.ten + '.';
    return;
  }
  const tang = nay > cu;
  // Chi tăng là xấu, thu tăng là tốt
  chu.appendChild(tao_('b', tang === layThu ? 'tot' : 'xau',
    (tang ? 'Tăng ' : 'Giảm ') + tienTH_(Math.abs(nay - cu))));
  chu.appendChild(document.createTextNode(' so với ' + truoc.ten));
}

function oBieuTuong_(ten, mau) {
  const o = tao_('span', 'o-bieu-tuong', bieuTuong_(ten));
  o.style.background = tronMau_(mau, '#ffffff', 0.78);
  o.setAttribute('aria-hidden', 'true');
  return o;
}

function veDsHangMuc_(dong, layThu) {
  $('tieuDeDs').textContent = (layThu ? 'Thu nhập' : 'Chi tiêu') + ' theo hạng mục';
  const hop = $('dsHangMuc');
  hop.textContent = '';
  const tong = dong.reduce(function (a, x) { return a + x.giaTri; }, 0);

  dong.forEach(function (x) {
    const b = tao_('button', 'dong-hm' + (x.ten === trangThai.hmChon ? ' dang-chon' : ''));
    b.type = 'button';
    b.dataset.hm = x.ten;
    b.appendChild(oBieuTuong_(x.ten, x.mau));
    const ten = tao_('span', 'ten');
    ten.appendChild(tao_('b', null, x.ten));
    ten.appendChild(tao_('small', null, phanTram_(x.giaTri, tong)));
    b.appendChild(ten);
    b.appendChild(tao_('span', 'tien', tienTH_(x.giaTri)));
    b.appendChild(tao_('span', 'mui', '›'));
    hop.appendChild(b);
  });

  const trong = $('trongHangMuc');
  trong.hidden = dong.length > 0;
  trong.textContent = 'Chưa có khoản ' + (layThu ? 'thu' : 'chi') + ' nào trong kỳ này.';
}

function cungTron_(cx, cy, R, r, a0, a1) {
  const lon = a1 - a0 > Math.PI ? 1 : 0;
  const d = function (ban, a) {
    return (cx + ban * Math.cos(a)).toFixed(2) + ',' + (cy + ban * Math.sin(a)).toFixed(2);
  };
  return 'M' + d(R, a0) + 'A' + R + ',' + R + ' 0 ' + lon + ' 1 ' + d(R, a1)
    + 'L' + d(r, a1) + 'A' + r + ',' + r + ' 0 ' + lon + ' 0 ' + d(r, a0) + 'Z';
}

/** Rút gọn tên dài để chú thích không tràn */
function tenNgan_(s) { return s.length > 11 ? s.slice(0, 10) + '…' : s; }

/** Biểu đồ tròn phân bổ theo hạng mục, chú thích hai bên có đường dẫn */
function veBieuDoTron_(dong, layThu) {
  const khung = $('khungBDTron');
  khung.textContent = '';

  const W = Math.max(khung.clientWidth, 280);
  const R = Math.min(92, W / 2 - 80), r = R * 0.52;
  const H = Math.max(230, 2 * R + 60);
  const cx = W / 2, cy = H / 2;
  const tong = dong.reduce(function (a, x) { return a + x.giaTri; }, 0);

  const s = svg_('svg', {
    viewBox: '0 0 ' + W + ' ' + H, width: W, height: H, role: 'img',
    'aria-label': 'Biểu đồ tròn ' + (layThu ? 'thu nhập' : 'chi tiêu') + ' theo hạng mục'
  }, khung);

  svg_('circle', { cx: cx, cy: cy, r: R + 9, style: 'fill: var(--dam-nhat)', opacity: 0.6 }, s);

  if (tong <= 0) {
    svg_('circle', {
      cx: cx, cy: cy, r: (R + r) / 2, fill: 'none', stroke: MAU_LUOI, 'stroke-width': R - r
    }, s);
    const t = svg_('text', {
      x: cx, y: cy + 5, 'text-anchor': 'middle', 'font-size': 13, fill: MAU_CHU_MO
    }, s);
    t.textContent = 'Chưa có số liệu';
    return;
  }

  // Nhiều hạng mục quá thì gộp các phần nhỏ thành "Còn lại"
  let lat = dong;
  if (dong.length > SO_LAT_TOI_DA) {
    const phanNho = dong.slice(SO_LAT_TOI_DA - 1);
    lat = dong.slice(0, SO_LAT_TOI_DA - 1).concat([{
      ten: 'Còn lại', mau: MAU_CON_LAI,
      giaTri: phanNho.reduce(function (a, x) { return a + x.giaTri; }, 0)
    }]);
  }

  const cacNhan = [];
  let goc = -Math.PI / 2;
  lat.forEach(function (x) {
    const cung = x.giaTri / tong * Math.PI * 2;
    const giua = goc + cung / 2;
    const nhom = svg_('g', { class: 'lat' }, s);
    // Phần đang chọn nhô ra ngoài một chút
    if (x.ten === trangThai.hmChon) {
      nhom.setAttribute('transform',
        'translate(' + (7 * Math.cos(giua)).toFixed(1) + ',' + (7 * Math.sin(giua)).toFixed(1) + ')');
    }
    if (lat.length === 1) {
      svg_('circle', { cx: cx, cy: cy, r: (R + r) / 2, fill: 'none', stroke: x.mau, 'stroke-width': R - r }, nhom);
      svg_('circle', { cx: cx, cy: cy, r: r + 4, fill: 'none', stroke: '#fff', 'stroke-opacity': 0.25, 'stroke-width': 8 }, nhom);
    } else {
      svg_('path', {
        d: cungTron_(cx, cy, R, r, goc, goc + cung),
        fill: x.mau, stroke: '#ffffff', 'stroke-width': 2.5, 'stroke-linejoin': 'round'
      }, nhom);
      // Dải sáng phía trong cho vòng tròn có chiều sâu
      svg_('path', {
        d: cungTron_(cx, cy, r + 8, r, goc, goc + cung),
        fill: '#ffffff', 'fill-opacity': 0.25, stroke: '#ffffff', 'stroke-width': 2.5
      }, nhom);
    }
    nhom.addEventListener('click', function () { chonHangMuc_(x.ten); });
    cacNhan.push({ x: x, giua: giua, ben: Math.cos(giua) >= 0 ? 1 : -1 });
    goc += cung;
  });

  // Chú thích hai bên, dàn theo chiều dọc để không đè nhau
  const KHOANG = 40;
  [1, -1].forEach(function (ben) {
    const nhan = cacNhan.filter(function (n) { return n.ben === ben; });
    nhan.forEach(function (n) { n.y = cy + (R + 14) * Math.sin(n.giua); });
    nhan.sort(function (a, b) { return a.y - b.y; });
    for (let i = 0; i < nhan.length; i++) {
      nhan[i].y = Math.max(nhan[i].y, i ? nhan[i - 1].y + KHOANG : 22);
    }
    for (let i = nhan.length - 1; i >= 0; i--) {
      nhan[i].y = Math.min(nhan[i].y, i < nhan.length - 1 ? nhan[i + 1].y - KHOANG : H - 26);
    }

    nhan.forEach(function (n) {
      const x = n.x;
      const leX = ben > 0 ? W - 2 : 2;
      const neo = ben > 0 ? 'end' : 'start';
      const t1 = svg_('text', {
        x: leX, y: n.y - 3, 'text-anchor': neo, 'font-size': 14, 'font-weight': 'bold', fill: MAU_CHU
      }, s);
      t1.textContent = bieuTuong_(x.ten) + ' ' + phanTram_(x.giaTri, tong);
      const t2 = svg_('text', {
        x: leX, y: n.y + 14, 'text-anchor': neo, 'font-size': 12, fill: MAU_CHU_MO
      }, s);
      t2.textContent = tenNgan_(x.ten);
      const rong = Math.max(t1.getComputedTextLength(), t2.getComputedTextLength());

      const ax = cx + (R + 3) * Math.cos(n.giua), ay = cy + (R + 3) * Math.sin(n.giua);
      const kx = cx + (R + 13) * Math.cos(n.giua);
      const cuoi = ben > 0 ? Math.max(W - 2 - rong - 6, kx + 4) : Math.min(2 + rong + 6, kx - 4);
      svg_('polyline', {
        points: ax.toFixed(1) + ',' + ay.toFixed(1) + ' ' + kx.toFixed(1) + ',' + n.y.toFixed(1)
          + ' ' + cuoi.toFixed(1) + ',' + n.y.toFixed(1),
        fill: 'none', stroke: '#b8bec8', 'stroke-width': 1, 'stroke-dasharray': '3 3'
      }, s);
      [t1, t2].forEach(function (t) {
        t.style.cursor = 'pointer';
        t.addEventListener('click', function () { chonHangMuc_(x.ten); });
      });
    });
  });
}

/** Chạm một phần của vòng tròn: làm nổi phần đó và dòng tương ứng bên dưới */
function chonHangMuc_(ten) {
  trangThai.hmChon = trangThai.hmChon === ten ? '' : ten;
  veTongHop_();
}

/** Vạch chia trục dọc tròn số: 0, 250k, 500k... */
function vachDep_(lonNhat, soVach) {
  const tho = lonNhat / soVach;
  const mu = Math.pow(10, Math.floor(Math.log10(tho)));
  const he = [1, 2, 2.5, 5, 10].find(function (k) { return k * mu >= tho; });
  const buoc = he * mu;
  return { buoc: buoc, dinh: buoc * Math.ceil(lonNhat / buoc - 1e-9) };
}

/** Cột bo tròn 4px ở đầu, vuông ở chân */
function duongCot_(x, y, w, h) {
  const r = Math.min(4, w / 2, h);
  return 'M' + x + ',' + (y + h) + 'V' + (y + r)
    + 'Q' + x + ',' + y + ' ' + (x + r) + ',' + y
    + 'H' + (x + w - r)
    + 'Q' + (x + w) + ',' + y + ' ' + (x + w) + ',' + (y + r)
    + 'V' + (y + h) + 'Z';
}

function oMau_(mau) {
  const o = tao_('i', 'o-mau');
  o.style.background = mau;
  return o;
}

function mucDocSo_(mau, nhan, giaTri) {
  const muc = tao_('span', 'muc');
  const gach = tao_('i', 'gach-mau');
  gach.style.background = mau;
  muc.appendChild(gach);
  muc.appendChild(tao_('span', 'gia-tri', giaTri));
  muc.appendChild(tao_('span', null, nhan));
  return muc;
}

/**
 * Biểu đồ cột đôi, hiện đủ mọi mốc trên một màn hình (24 giờ, 31 ngày...).
 * Mốc hẹp quá thì nhãn xếp so le hai hàng để mốc nào cũng có nhãn.
 */
function veBieuDoCot_(moc, thu, chi) {
  const khung = $('khungBDCot');
  khung.textContent = '';
  $('bangSoCot').textContent = '';

  const chuGiai = $('chuGiaiCot');
  chuGiai.textContent = '';
  ['Thu', 'Chi'].forEach(function (loai) {
    const s = tao_('span');
    s.appendChild(oMau_(MAU_COT[loai]));
    s.appendChild(document.createTextNode(loai));
    chuGiai.appendChild(s);
  });

  const docSo = $('docSoCot');
  const goiY = function () {
    docSo.textContent = 'Chạm hoặc kéo ngang trên biểu đồ để xem số của từng ' + moc.donVi + '.';
  };
  goiY();

  const n = moc.ngan.length;
  const W = Math.max(khung.clientWidth, 260);
  const trai = 38, phai = 2, tren = 8;
  const rongVe = W - trai - phai;
  const oRong = rongVe / n;

  // Đủ chỗ thì nhãn một hàng, chật thì so le hai hàng, chật nữa thì thưa bớt
  const dai = Math.max.apply(null, moc.ngan.map(function (x) {
    return Math.max.apply(null, x.split('\n').map(function (d) { return d.length; }));
  }));
  const rongNhan = dai * 5.6 + 4;
  const coHaiDong = moc.ngan[0].indexOf('\n') >= 0;
  const soLe = !coHaiDong && oRong < rongNhan;
  const buocNhan = soLe ? Math.max(1, Math.ceil(rongNhan / (2 * oRong))) : 1;
  const duoi = coHaiDong || soLe ? 34 : 22;
  const H = 200 + duoi;
  const caoVe = H - tren - duoi;

  const lonNhat = Math.max.apply(null, thu.concat(chi));
  const vach = vachDep_(Math.max(lonNhat, 1000), 4);
  const yCua = function (v) { return tren + caoVe - v / vach.dinh * caoVe; };

  const s = svg_('svg', {
    viewBox: '0 0 ' + W + ' ' + H, width: W, height: H,
    role: 'img', 'aria-label': 'Biểu đồ cột thu và chi theo ' + moc.donVi
  }, khung);

  for (let v = 0; v <= vach.dinh + 1e-6; v += vach.buoc) {
    const y = Math.round(yCua(v)) + 0.5;
    svg_('line', {
      x1: trai, x2: W - phai, y1: y, y2: y,
      stroke: v === 0 ? MAU_TRUC : MAU_LUOI, 'stroke-width': 1
    }, s);
    const nhan = svg_('text', {
      x: trai - 5, y: y + 3.5, 'text-anchor': 'end', 'font-size': 10, fill: MAU_CHU_MO
    }, s);
    nhan.textContent = tienGon_(v);
  }

  const khe = oRong < 14 ? 1 : 2;                        // khe trắng giữa hai cột
  const rongCot = Math.max(1.5, Math.min(24, (oRong * 0.76 - khe) / 2));
  const nen = svg_('rect', {
    x: trai, y: tren, width: oRong, height: caoVe, rx: 3, opacity: 0, style: 'fill: var(--dam-nhat)'
  }, s);

  for (let i = 0; i < n; i++) {
    const giua = trai + oRong * (i + 0.5);
    [[thu[i], MAU_COT.Thu, giua - khe / 2 - rongCot], [chi[i], MAU_COT.Chi, giua + khe / 2]]
      .forEach(function (c) {
        if (c[0] <= 0) return;
        const h = Math.max(1.5, caoVe * c[0] / vach.dinh);
        svg_('path', { d: duongCot_(c[2], tren + caoVe - h, rongCot, h), fill: c[1] }, s);
      });

    if (i % buocNhan) continue;
    const hangDuoi = soLe && (i / buocNhan) % 2 === 1;
    const t = svg_('text', {
      x: giua, y: tren + caoVe + (hangDuoi ? 26 : 14),
      'text-anchor': 'middle', 'font-size': 10, fill: MAU_CHU_MO
    }, s);
    moc.ngan[i].split('\n').forEach(function (dong, k) {
      const ts = svg_('tspan', { x: giua, dy: k ? 12 : 0 }, t);
      ts.textContent = dong;
    });
    if (hangDuoi) {
      svg_('line', {
        x1: giua, x2: giua, y1: tren + caoVe + 3, y2: tren + caoVe + 16, stroke: MAU_LUOI, 'stroke-width': 1
      }, s);
    }
  }

  if (lonNhat <= 0) {
    const t = svg_('text', {
      x: trai + rongVe / 2, y: tren + caoVe / 2, 'text-anchor': 'middle', 'font-size': 13, fill: MAU_CHU_MO
    }, s);
    t.textContent = 'Chưa có số liệu trong kỳ này';
  }

  // Vùng chạm: chạm hoặc kéo ngang để đọc số của mốc gần nhất
  const vung = svg_('rect', {
    x: trai, y: 0, width: rongVe, height: H, fill: 'transparent', class: 'vung-cham'
  }, s);
  let dangChon = -1;
  const chon = function (e) {
    const hop = s.getBoundingClientRect();
    const x = (e.clientX - hop.left) * (W / hop.width);
    const i = Math.min(n - 1, Math.max(0, Math.floor((x - trai) / oRong)));
    if (i === dangChon) return;
    dangChon = i;
    nen.setAttribute('x', trai + oRong * i);
    nen.setAttribute('opacity', 1);
    docSo.textContent = '';
    docSo.appendChild(tao_('b', null, moc.dai[i]));
    docSo.appendChild(mucDocSo_(MAU_COT.Thu, 'thu', tien_(thu[i])));
    docSo.appendChild(mucDocSo_(MAU_COT.Chi, 'chi', tien_(chi[i])));
  };
  vung.addEventListener('pointerdown', chon);
  vung.addEventListener('pointermove', function (e) {
    if (e.pointerType === 'mouse' || e.buttons) chon(e);
  });
  vung.addEventListener('pointerleave', function (e) {
    if (e.pointerType !== 'mouse') return;
    dangChon = -1;
    nen.setAttribute('opacity', 0);
    goiY();
  });

  // Bảng số của các mốc có phát sinh, để đọc được mà không cần chạm
  const coSo = [];
  for (let i = 0; i < n; i++) if (thu[i] || chi[i]) coSo.push(i);
  if (!coSo.length) return;
  const chiTiet = tao_('details', 'bang-so');
  chiTiet.appendChild(tao_('summary', null, 'Xem bảng số theo ' + moc.donVi));
  const bang = tao_('table');
  const dau = tao_('tr');
  [moc.donVi.charAt(0).toUpperCase() + moc.donVi.slice(1), 'Thu', 'Chi'].forEach(function (x) {
    dau.appendChild(tao_('th', null, x));
  });
  bang.appendChild(dau);
  coSo.forEach(function (i) {
    const tr = tao_('tr');
    tr.appendChild(tao_('td', null, moc.dai[i]));
    tr.appendChild(tao_('td', null, tien_(thu[i])));
    tr.appendChild(tao_('td', null, tien_(chi[i])));
    bang.appendChild(tr);
  });
  chiTiet.appendChild(bang);
  $('bangSoCot').appendChild(chiTiet);
}

/** Danh sách các khoản của một hạng mục trong kỳ đang xem */
function moChiTiet_(hm) {
  const kh = tinhKhoang_(trangThai.ky, trangThai.moc);
  const loai = trangThai.loaiXem;
  const ds = trongKhoang_(kh.bd, kh.kt)
    .filter(function (g) { return g.hm === hm && g.loai === loai; })
    .sort(function (a, b) { return a.ngay < b.ngay ? 1 : a.ngay > b.ngay ? -1 : theoGio_(a, b); });
  const tong = ds.reduce(function (a, g) { return a + g.tien; }, 0);

  $('ctTieuDe').textContent = bieuTuong_(hm) + ' ' + hm;
  $('ctPhu').textContent = tenKy_(trangThai.ky, kh)[0] + ' · ' + ds.length + ' khoản '
    + loai.toLowerCase() + ' · ' + tien_(tong);

  const hop = $('ctDs');
  hop.textContent = '';
  let ngayTruoc = '';
  let ol = null;
  let stt = 0;
  ds.forEach(function (g) {
    if (g.ngay !== ngayTruoc) {
      ngayTruoc = g.ngay;
      hop.appendChild(tao_('div', 'ngay-nhom', tenNgay_(tuChuoiNgay_(g.ngay))));
      ol = tao_('ol', 'ds-giao-dich');
      hop.appendChild(ol);
      stt = 0;
    }
    ol.appendChild(taoDongGiaoDich_(g, ++stt));
  });

  trangThai.hmChon = hm;
  veTongHop_();
  moBang_('bangChiTiet');
}

function moChonKy_() {
  document.querySelectorAll('#dsKy button').forEach(function (b) {
    b.setAttribute('role', 'radio');
    b.setAttribute('aria-checked', String(b.dataset.ky === trangThai.ky));
  });
  $('chonMoc').value = chuoiNgay_(trangThai.moc);
  moBang_('bangKy');
}

function datKy_(ky, moc) {
  trangThai.ky = ky;
  trangThai.moc = moc;
  trangThai.hmChon = '';
  dongBang_();
  veTongHop_();
}


/* ========== CÀI ĐẶT ========== */

function veCaiDat_() {
  const luoi = $('luoiMau');
  luoi.textContent = '';
  MAU_GIAO_DIEN.forEach(function (m) {
    const chon = m.ma === duLieu.caiDat.mau;
    const b = tao_('button');
    b.type = 'button';
    b.dataset.ma = m.ma;
    b.setAttribute('role', 'radio');
    b.setAttribute('aria-checked', String(chon));
    b.setAttribute('aria-label', 'Màu ' + m.ten);
    const tron = tao_('span', 'tron-mau', chon ? '✓' : '');
    tron.style.background = m.ma;
    b.appendChild(tron);
    b.appendChild(tao_('span', null, m.ten));
    luoi.appendChild(b);
  });

  const ds = duLieu.giaoDich;
  if (!ds.length) {
    $('thongTinKho').textContent = 'Chưa có khoản nào.';
    return;
  }
  const cacNgay = ds.map(function (g) { return g.ngay; }).sort();
  $('thongTinKho').textContent = 'Đang lưu ' + nhomSo_(ds.length) + ' khoản, từ ngày '
    + dinhDang_(tuChuoiNgay_(cacNgay[0])) + ' đến ngày '
    + dinhDang_(tuChuoiNgay_(cacNgay[cacNgay.length - 1])) + '.';
}

function doiMau_(ma) {
  duLieu.caiDat.mau = ma;
  apDungMau_(ma);
  luuDuLieu_();
  veCaiDat_();
}


/* ========== LƯU FILE RA NGOÀI ========== */

function base64_(u8) {
  let s = '';
  for (let i = 0; i < u8.length; i += 0x8000) {
    s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
  }
  return btoa(s);
}

/** noiDung là chữ (JSON) hoặc mảng byte (Excel) */
function taiFile_(ten, loai, noiDung) {
  if (coCauNoi_() && window.Android.luuFile) {
    if (typeof noiDung === 'string') window.Android.luuFile(ten, loai, noiDung);
    else window.Android.luuFileBase64(ten, loai, base64_(noiDung));
    return;
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([noiDung], { type: loai }));
  a.download = ten;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
  thongBao_('Đã tải file ' + ten + '.');
}

/** Android gọi lại sau khi người dùng chọn chỗ lưu file */
window.daLuuFile = function (ketQua) {
  if (ketQua === 'ok') thongBao_('Đã lưu file.');
  else if (ketQua === 'huy') thongBao_('Đã hủy, chưa lưu file.');
  else thongBao_('Không lưu được file.');
};

function xuatJson_() {
  const noiDung = JSON.stringify({
    ungDung: 'so-chi-tieu',
    phienBan: 1,
    xuatLuc: new Date().toISOString(),
    giaoDich: sapTheoNgay_(duLieu.giaoDich)
  }, null, 1);
  taiFile_('so-chi-tieu-' + homNay_() + '.json', 'application/json', noiDung);
}


/* ========== FILE EXCEL (.xlsx) ========== */

const BANG_CRC = (function () {
  const b = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    b[n] = c >>> 0;
  }
  return b;
})();

function crc32_(u8) {
  let c = 0xffffffff;
  for (let i = 0; i < u8.length; i++) c = BANG_CRC[(c ^ u8[i]) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

/** Gói các file thành một file zip không nén, là vỏ của file .xlsx */
function taoZip_(cacFile) {
  const ma = new TextEncoder();
  const phan = [];
  const trungTam = [];
  let viTri = 0;

  cacFile.forEach(function (f) {
    const ten = ma.encode(f.ten);
    const du = ma.encode(f.noiDung);
    const crc = crc32_(du);

    const dau = new DataView(new ArrayBuffer(30));
    dau.setUint32(0, 0x04034b50, true);
    dau.setUint16(4, 20, true);
    dau.setUint16(6, 0x0800, true);          // tên file mã UTF-8
    dau.setUint16(12, 0x21, true);           // ngày 01/01/1980
    dau.setUint32(14, crc, true);
    dau.setUint32(18, du.length, true);
    dau.setUint32(22, du.length, true);
    dau.setUint16(26, ten.length, true);
    phan.push(new Uint8Array(dau.buffer), ten, du);

    const tt = new DataView(new ArrayBuffer(46));
    tt.setUint32(0, 0x02014b50, true);
    tt.setUint16(4, 20, true);
    tt.setUint16(6, 20, true);
    tt.setUint16(8, 0x0800, true);
    tt.setUint16(14, 0x21, true);
    tt.setUint32(16, crc, true);
    tt.setUint32(20, du.length, true);
    tt.setUint32(24, du.length, true);
    tt.setUint16(28, ten.length, true);
    tt.setUint32(42, viTri, true);
    trungTam.push(new Uint8Array(tt.buffer), ten);

    viTri += 30 + ten.length + du.length;
  });

  const coTT = trungTam.reduce(function (a, x) { return a + x.length; }, 0);
  const cuoi = new DataView(new ArrayBuffer(22));
  cuoi.setUint32(0, 0x06054b50, true);
  cuoi.setUint16(8, cacFile.length, true);
  cuoi.setUint16(10, cacFile.length, true);
  cuoi.setUint32(12, coTT, true);
  cuoi.setUint32(16, viTri, true);

  const tatCa = phan.concat(trungTam, [new Uint8Array(cuoi.buffer)]);
  const ket = new Uint8Array(tatCa.reduce(function (a, x) { return a + x.length; }, 0));
  let p = 0;
  tatCa.forEach(function (x) { ket.set(x, p); p += x.length; });
  return ket;
}

function xmlChu_(s) {
  return String(s)
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Số ngày kiểu Excel, tính từ 30/12/1899 */
function soNgayExcel_(ngay) {
  const p = ngay.split('-').map(Number);
  return (Date.UTC(p[0], p[1] - 1, p[2]) - Date.UTC(1899, 11, 30)) / 86400000;
}

function oChu_(ref, chu, kieu) {
  return '<c r="' + ref + '" t="inlineStr"' + (kieu ? ' s="' + kieu + '"' : '')
    + '><is><t xml:space="preserve">' + xmlChu_(chu) + '</t></is></c>';
}

function oSo_(ref, so, kieu) {
  return '<c r="' + ref + '"' + (kieu ? ' s="' + kieu + '"' : '') + '><v>' + so + '</v></c>';
}

function trangTinh_(doRong, cacDong) {
  return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    + '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    + '<sheetViews><sheetView workbookViewId="0">'
    + '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
    + '<cols>' + doRong.map(function (w, i) {
      return '<col min="' + (i + 1) + '" max="' + (i + 1) + '" width="' + w + '" customWidth="1"/>';
    }).join('') + '</cols>'
    + '<sheetData>' + cacDong.join('') + '</sheetData></worksheet>';
}

/**
 * Tạo file .xlsx gồm hai trang: SO_CAI (mỗi khoản một dòng, đúng các cột của
 * trang SO_CAI bản trang tính) và TONG_HOP (tổng thu chi từng tháng).
 */
function taoExcel_() {
  const COT = 'ABCDEF';
  const dau = function (cacTen) {
    return '<row r="1">' + cacTen.map(function (t, i) { return oChu_(COT[i] + '1', t, 1); }).join('') + '</row>';
  };

  const soCai = [dau(['NGÀY', 'GIỜ', 'LOẠI', 'HẠNG MỤC', 'SỐ TIỀN', 'GHI CHÚ'])];
  const theoThang = {};
  sapTheoNgay_(duLieu.giaoDich).forEach(function (g, k) {
    const r = k + 2;
    let x = '<row r="' + r + '">' + oSo_('A' + r, soNgayExcel_(g.ngay), 2);
    if (g.gio) {
      x += oSo_('B' + r, (Number(g.gio.slice(0, 2)) * 60 + Number(g.gio.slice(3))) / 1440, 3);
    }
    x += oChu_('C' + r, g.loai) + oChu_('D' + r, g.hm) + oSo_('E' + r, g.tien, 4);
    if (g.ghiChu) x += oChu_('F' + r, g.ghiChu);
    soCai.push(x + '</row>');

    const thang = g.ngay.slice(0, 7);
    if (!theoThang[thang]) theoThang[thang] = { thu: 0, chi: 0 };
    if (g.loai === 'Thu') theoThang[thang].thu += g.tien; else theoThang[thang].chi += g.tien;
  });

  const tongHop = [dau(['THÁNG', 'TỔNG THU', 'TỔNG CHI', 'SỐ DƯ'])];
  Object.keys(theoThang).sort().forEach(function (thang, k) {
    const r = k + 2;
    const t = theoThang[thang];
    tongHop.push('<row r="' + r + '">'
      + oChu_('A' + r, thang.slice(5) + '/' + thang.slice(0, 4))
      + oSo_('B' + r, t.thu, 4) + oSo_('C' + r, t.chi, 4) + oSo_('D' + r, t.thu - t.chi, 5)
      + '</row>');
  });

  const QH = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
  const KIEU = 'application/vnd.openxmlformats-officedocument.spreadsheetml.';
  const DAU_XML = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
  const mauChinh = duLieu.caiDat.mau.slice(1).toUpperCase();

  return taoZip_([
    { ten: '[Content_Types].xml', noiDung: DAU_XML
      + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
      + '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
      + '<Default Extension="xml" ContentType="application/xml"/>'
      + '<Override PartName="/xl/workbook.xml" ContentType="' + KIEU + 'sheet.main+xml"/>'
      + '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="' + KIEU + 'worksheet+xml"/>'
      + '<Override PartName="/xl/worksheets/sheet2.xml" ContentType="' + KIEU + 'worksheet+xml"/>'
      + '<Override PartName="/xl/styles.xml" ContentType="' + KIEU + 'styles+xml"/>'
      + '</Types>' },
    { ten: '_rels/.rels', noiDung: DAU_XML
      + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
      + '<Relationship Id="rId1" Type="' + QH + '/officeDocument" Target="xl/workbook.xml"/>'
      + '</Relationships>' },
    { ten: 'xl/workbook.xml', noiDung: DAU_XML
      + '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="' + QH + '">'
      + '<sheets><sheet name="SO_CAI" sheetId="1" r:id="rId1"/>'
      + '<sheet name="TONG_HOP" sheetId="2" r:id="rId2"/></sheets></workbook>' },
    { ten: 'xl/_rels/workbook.xml.rels', noiDung: DAU_XML
      + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
      + '<Relationship Id="rId1" Type="' + QH + '/worksheet" Target="worksheets/sheet1.xml"/>'
      + '<Relationship Id="rId2" Type="' + QH + '/worksheet" Target="worksheets/sheet2.xml"/>'
      + '<Relationship Id="rId3" Type="' + QH + '/styles" Target="styles.xml"/>'
      + '</Relationships>' },
    { ten: 'xl/styles.xml', noiDung: DAU_XML
      + '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
      + '<numFmts count="4">'
      + '<numFmt numFmtId="164" formatCode="dd/mm/yyyy"/>'
      + '<numFmt numFmtId="165" formatCode="hh:mm"/>'
      + '<numFmt numFmtId="166" formatCode="#,##0 &quot;đ&quot;"/>'
      + '<numFmt numFmtId="167" formatCode="+#,##0 &quot;đ&quot;;-#,##0 &quot;đ&quot;;0 &quot;đ&quot;"/>'
      + '</numFmts>'
      + '<fonts count="2"><font><sz val="11"/><name val="Arial"/></font>'
      + '<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font></fonts>'
      + '<fills count="3"><fill><patternFill patternType="none"/></fill>'
      + '<fill><patternFill patternType="gray125"/></fill>'
      + '<fill><patternFill patternType="solid"><fgColor rgb="FF' + mauChinh + '"/>'
      + '<bgColor indexed="64"/></patternFill></fill></fills>'
      + '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
      + '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
      + '<cellXfs count="6">'
      + '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
      + '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">'
      + '<alignment horizontal="center"/></xf>'
      + '<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
      + '<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
      + '<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
      + '<xf numFmtId="167" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
      + '</cellXfs>'
      + '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
      + '</styleSheet>' },
    { ten: 'xl/worksheets/sheet1.xml', noiDung: trangTinh_([13, 8, 8, 16, 15, 36], soCai) },
    { ten: 'xl/worksheets/sheet2.xml', noiDung: trangTinh_([11, 16, 16, 16], tongHop) }
  ]);
}

function xuatExcel_() {
  taiFile_('so-chi-tieu-' + homNay_() + '.xlsx',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', taoExcel_());
}

/** Mở file zip: đọc danh mục, giải nén từng file khi cần */
function moZip_(buf) {
  const u8 = new Uint8Array(buf);
  const dv = new DataView(buf);
  let cuoi = -1;
  for (let i = u8.length - 22; i >= Math.max(0, u8.length - 65557); i--) {
    if (dv.getUint32(i, true) === 0x06054b50) { cuoi = i; break; }
  }
  if (cuoi < 0) throw new Error('File Excel bị hỏng hoặc không đúng định dạng.');

  const giaiMa = new TextDecoder();
  const muc = {};
  const soMuc = dv.getUint16(cuoi + 10, true);
  let p = dv.getUint32(cuoi + 16, true);
  for (let k = 0; k < soMuc && dv.getUint32(p, true) === 0x02014b50; k++) {
    const dai = dv.getUint16(p + 28, true);
    muc[giaiMa.decode(u8.subarray(p + 46, p + 46 + dai))] = {
      cachNen: dv.getUint16(p + 10, true),
      co: dv.getUint32(p + 20, true),
      dau: dv.getUint32(p + 42, true)
    };
    p += 46 + dai + dv.getUint16(p + 30, true) + dv.getUint16(p + 32, true);
  }

  return {
    doc: async function (ten) {
      const m = muc[ten];
      if (!m) return null;
      const batDau = m.dau + 30 + dv.getUint16(m.dau + 26, true) + dv.getUint16(m.dau + 28, true);
      const du = u8.subarray(batDau, batDau + m.co);
      if (m.cachNen === 0) return giaiMa.decode(du);
      if (m.cachNen !== 8 || typeof DecompressionStream === 'undefined') {
        throw new Error('Máy chưa đọc được file Excel này. Hãy cập nhật Android System WebView rồi thử lại.');
      }
      const luong = new Blob([du]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
      return giaiMa.decode(await new Response(luong).arrayBuffer());
    }
  };
}

/** Đọc mọi trang trong file .xlsx thành bảng giá trị */
async function docExcel_(buf) {
  const zip = moZip_(buf);
  const xml = function (s) { return new DOMParser().parseFromString(s, 'application/xml'); };
  const the = function (goc, ten) { return Array.from(goc.getElementsByTagNameNS('*', ten)); };

  const soTay = await zip.doc('xl/workbook.xml');
  if (!soTay) throw new Error('File này không phải file Excel (.xlsx).');
  const lienKet = {};
  const rels = await zip.doc('xl/_rels/workbook.xml.rels');
  if (rels) {
    the(xml(rels), 'Relationship').forEach(function (r) {
      const dich = r.getAttribute('Target') || '';
      lienKet[r.getAttribute('Id')] = dich.charAt(0) === '/' ? dich.slice(1) : 'xl/' + dich;
    });
  }
  const chuoiChung = [];
  const ss = await zip.doc('xl/sharedStrings.xml');
  if (ss) {
    the(xml(ss), 'si').forEach(function (si) {
      chuoiChung.push(the(si, 't').map(function (t) { return t.textContent; }).join(''));
    });
  }

  const ket = [];
  for (const trang of the(xml(soTay), 'sheet')) {
    const rid = trang.getAttributeNS('http://schemas.openxmlformats.org/officeDocument/2006/relationships', 'id')
      || trang.getAttribute('r:id');
    const noiDung = lienKet[rid] ? await zip.doc(lienKet[rid]) : null;
    if (!noiDung) continue;

    const bang = [];
    the(xml(noiDung), 'c').forEach(function (c) {
      const m = /^([A-Z]+)(\d+)$/.exec(c.getAttribute('r') || '');
      if (!m) return;
      let cot = 0;
      for (let i = 0; i < m[1].length; i++) cot = cot * 26 + m[1].charCodeAt(i) - 64;
      const kieu = c.getAttribute('t');
      const v = the(c, 'v')[0];
      const chu = v ? v.textContent : '';
      let gt;
      if (kieu === 's') gt = chuoiChung[Number(chu)] || '';
      else if (kieu === 'inlineStr') gt = the(c, 't').map(function (t) { return t.textContent; }).join('');
      else if (kieu === 'str' || kieu === 'e' || kieu === 'b') gt = chu;
      else gt = chu === '' ? '' : Number(chu);
      const hang = Number(m[2]) - 1;
      (bang[hang] = bang[hang] || [])[cot - 1] = gt;
    });
    ket.push({ ten: trang.getAttribute('name') || '', bang: Array.from(bang, function (d) { return d || []; }) });
  }
  return ket;
}


/* ========== NHẬP DỮ LIỆU ========== */

/** Tách chữ CSV thành bảng, tự nhận dấu phẩy, chấm phẩy hoặc tab */
function tachCSV_(chu) {
  const mau = chu.slice(0, 2000);
  const dem = function (k) { return mau.split(k).length; };
  const ngan = [',', ';', '\t'].sort(function (a, b) { return dem(b) - dem(a); })[0];

  const bang = [];
  let dong = [], o = '', trongNgoac = false;
  for (let i = 0; i < chu.length; i++) {
    const c = chu[i];
    if (trongNgoac) {
      if (c === '"' && chu[i + 1] === '"') { o += '"'; i++; }
      else if (c === '"') trongNgoac = false;
      else o += c;
    } else if (c === '"') trongNgoac = true;
    else if (c === ngan) { dong.push(o); o = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && chu[i + 1] === '\n') i++;
      dong.push(o); bang.push(dong); dong = []; o = '';
    } else o += c;
  }
  if (o !== '' || dong.length) { dong.push(o); bang.push(dong); }
  return bang;
}

/** Ngày dạng số của Excel, "2026-09-22", "22/09/2026" hoặc "Thứ Ba, 22/09/2026" */
function docNgay_(v) {
  if (typeof v === 'number') {
    if (v < 1000) return '';
    const d = new Date(Date.UTC(1899, 11, 30) + Math.floor(v) * 86400000);
    return d.getUTCFullYear() + '-' + hai_(d.getUTCMonth() + 1) + '-' + hai_(d.getUTCDate());
  }
  const s = String(v || '').trim();
  let m = s.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/);
  if (m) return m[1] + '-' + hai_(Number(m[2])) + '-' + hai_(Number(m[3]));
  m = s.match(/(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})/);
  if (m) return m[3] + '-' + hai_(Number(m[2])) + '-' + hai_(Number(m[1]));
  return '';
}

/** Giờ dạng phần của ngày (0,354) hoặc chữ "08:30" */
function docGioNhap_(v) {
  const s = String(v == null ? '' : v).trim();
  const m = s.match(/^(\d{1,2}):(\d{2})/);
  if (m && Number(m[1]) < 24) return hai_(Number(m[1])) + ':' + m[2];
  let n = typeof v === 'number' ? v : NaN;
  if (isNaN(n) && /^\d*[.,]\d+$/.test(s)) n = Number(s.replace(',', '.'));
  if (!isNaN(n) && n > 0) {
    const phut = Math.round((n % 1) * 1440) % 1440;
    return hai_(Math.floor(phut / 60)) + ':' + hai_(phut % 60);
  }
  return '';
}

/**
 * Đọc các khoản trong một bảng (một trang Excel hoặc một file CSV). Nhận cả
 * trang SO_CAI (có cột NGÀY) lẫn trang ngày của bản trang tính (không có cột
 * NGÀY, lấy ngày từ tên trang, tên file hoặc dòng "Thứ Hai, 22/09/2026").
 * Dòng sổ cái thuộc ngày có trong boQuaNgay thì bỏ qua. Trả về null nếu bảng
 * không có dữ liệu thu chi.
 */
function docGiaoDichBang_(bang, tenNguon, boQuaNgay) {
  const chuan = function (x) {
    return boDau_(x == null ? '' : x).toUpperCase().replace(/\s+/g, ' ').trim();
  };

  let hangDau = -1;
  for (let i = 0; i < Math.min(bang.length, 30); i++) {
    if ((bang[i] || []).some(function (x) { return chuan(x) === 'HANG MUC'; })) { hangDau = i; break; }
  }
  if (hangDau < 0) return null;

  const cot = {};
  bang[hangDau].forEach(function (x, i) {
    const ten = chuan(x);
    if (ten && !(ten in cot)) cot[ten] = i;
  });
  if (!('SO TIEN' in cot)) return null;

  let ngayChung = '';
  if (!('NGAY' in cot)) {
    const m = String(tenNguon || '').match(/\d{4}-\d{2}-\d{2}/);
    if (m) ngayChung = m[0];
    for (let i = 0; !ngayChung && i < hangDau; i++) {
      const d = bang[i] || [];
      for (let k = 0; !ngayChung && k < d.length; k++) {
        if (typeof d[k] === 'string') ngayChung = docNgay_(d[k]);
      }
    }
    if (!ngayChung) return null;
  }

  const lay = function (dong, ten) { return ten in cot ? dong[cot[ten]] : ''; };
  const ket = [];
  for (let i = hangDau + 1; i < bang.length; i++) {
    const dong = bang[i] || [];
    const hm = String(lay(dong, 'HANG MUC') == null ? '' : lay(dong, 'HANG MUC')).trim();
    if (!hm) continue;
    const ngay = ngayChung || docNgay_(lay(dong, 'NGAY'));
    if (!ngay || (!ngayChung && boQuaNgay && boQuaNgay[ngay])) continue;
    const g = chuanHoa_({
      ngay: ngay,
      gio: docGioNhap_(lay(dong, 'GIO')),
      loai: chuan(lay(dong, 'LOAI')).indexOf('THU') === 0 ? 'Thu' : 'Chi',
      hm: hm,
      tien: Math.abs(docSoTien_(lay(dong, 'SO TIEN'))),
      ghiChu: lay(dong, 'GHI CHU'),
      tao: Date.now() + i
    });
    if (g) ket.push(g);
  }
  return ket;
}

/** Đọc cả file Excel. Như gomGiaoDich_: ngày nào còn trang riêng thì bỏ qua dòng sổ cái của ngày đó */
async function docGiaoDichExcel_(buf) {
  const cacTrang = await docExcel_(buf);
  const coTrang = {};
  cacTrang.forEach(function (t) { if (MAU_TEN_NGAY.test(t.ten)) coTrang[t.ten] = true; });

  let ket = [];
  let coBang = false;
  cacTrang.forEach(function (t) {
    const ds = docGiaoDichBang_(t.bang, t.ten, coTrang);
    if (!ds) return;
    coBang = true;
    ket = ket.concat(ds);
  });
  if (!coBang) throw new Error('Không thấy trang nào có cột HẠNG MỤC và SỐ TIỀN trong file.');
  return ket;
}

function khoaTrung_(g) {
  return [g.ngay, g.gio, g.loai, g.hm, g.tien, g.ghiChu].join('|');
}

/** Thêm các khoản mới vào kho, bỏ qua khoản đã có (cùng mã, hoặc giống hệt nội dung) */
function gopVao_(moi) {
  if (!moi.length) { thongBao_('File không có khoản nào để nhập.'); return; }

  const coId = {};
  const daCo = {};
  duLieu.giaoDich.forEach(function (g) {
    coId[g.id] = true;
    const k = khoaTrung_(g);
    daCo[k] = (daCo[k] || 0) + 1;
  });
  const them = [];
  moi.forEach(function (g) {
    if (coId[g.id]) return;
    const k = khoaTrung_(g);
    if (daCo[k]) { daCo[k]--; return; }
    coId[g.id] = true;
    them.push(g);
  });

  if (!them.length) {
    thongBao_('Tất cả ' + nhomSo_(moi.length) + ' khoản trong file đã có sẵn, không thêm gì.');
    return;
  }
  const truoc = duLieu.giaoDich.slice();
  duLieu.giaoDich = duLieu.giaoDich.concat(them);
  luuDuLieu_();
  veTrangHienTai_();
  thongBao_('Đã thêm ' + nhomSo_(them.length) + ' khoản'
    + (moi.length > them.length ? ', bỏ qua ' + nhomSo_(moi.length - them.length) + ' khoản trùng' : '') + '.', {
    nhan: 'Hoàn tác',
    lam: function () { duLieu.giaoDich = truoc; luuDuLieu_(); veTrangHienTai_(); }
  });
}

/** Nhận file JSON (xuất từ app), Excel .xlsx hoặc CSV */
async function nhapFile_(tenFile, buf) {
  const u8 = new Uint8Array(buf);
  if (u8[0] === 0x50 && u8[1] === 0x4b) return docGiaoDichExcel_(buf);
  if (u8[0] === 0xd0 && u8[1] === 0xcf) {
    throw new Error('File .xls đời cũ chưa đọc được. Hãy mở và lưu lại dạng .xlsx rồi thử lại.');
  }

  const chu = new TextDecoder().decode(u8).replace(/^﻿/, '');
  const dau = chu.trim().charAt(0);
  if (dau === '{' || dau === '[') {
    let obj;
    try { obj = JSON.parse(chu); } catch (e) { throw new Error('File JSON bị hỏng.'); }
    const ds = Array.isArray(obj) ? obj : obj && obj.giaoDich;
    if (!Array.isArray(ds)) throw new Error('File JSON này không phải dữ liệu của Sổ chi tiêu.');
    return chuanHoaDanhSach_(ds);
  }

  const ds = docGiaoDichBang_(tachCSV_(chu), tenFile);
  if (!ds) {
    throw new Error('Không đọc được file. Hãy chọn file JSON, Excel (.xlsx) hoặc CSV có cột HẠNG MỤC và SỐ TIỀN.');
  }
  return ds;
}

function khiChonFile_() {
  const o = $('chonFile');
  const f = o.files && o.files[0];
  if (!f) return;
  const doc = new FileReader();
  doc.onload = function () {
    nhapFile_(f.name, doc.result)
      .then(gopVao_)
      .catch(function (e) { thongBao_(e.message || 'Không đọc được file.'); });
  };
  doc.onerror = function () { thongBao_('Không đọc được file.'); };
  doc.readAsArrayBuffer(f);
}


/* ========== NÚT QUAY LẠI CỦA ANDROID ========== */

/** Trả về true nếu đã tự xử lý, false để Android đóng app */
window.xuLyQuayLai = function () {
  if (trangThai.bangMo) { dongBang_(); return true; }
  if (trangThai.trang !== 'trangNgay') { chuyenTrang_('trangNgay'); return true; }
  if (trangThai.ngay !== homNay_()) { denNgay_(homNay_()); return true; }
  return false;
};


/* ========== KHỞI ĐỘNG ========== */

function ganSuKien_() {
  document.querySelector('.thanh-duoi').addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (!b) return;
    // Đang ở trang ngày mà bấm lại "Trang ngày" thì quay về hôm nay
    if (b.dataset.trang === 'trangNgay' && trangThai.trang === 'trangNgay') denNgay_(homNay_());
    else chuyenTrang_(b.dataset.trang);
  });

  // Trang ngày
  $('ngayTruoc').addEventListener('click', function () {
    denNgay_(chuoiNgay_(congNgay_(tuChuoiNgay_(trangThai.ngay), -1)));
  });
  $('ngaySau').addEventListener('click', function () {
    denNgay_(chuoiNgay_(congNgay_(tuChuoiNgay_(trangThai.ngay), 1)));
  });
  $('chonNgay').addEventListener('change', function () {
    if (MAU_TEN_NGAY.test(this.value)) denNgay_(this.value);
  });

  // Bấm vào một khoản (ở trang ngày hoặc danh sách của một hạng mục) để sửa
  const khiBamKhoan = function (e) {
    const dong = e.target.closest('.dong');
    if (!dong) return;
    const g = duLieu.giaoDich.find(function (x) { return x.id === dong.dataset.id; });
    if (g) moBangNhap_(g);
  };
  $('dsNgay').addEventListener('click', khiBamKhoan);
  $('ctDs').addEventListener('click', khiBamKhoan);

  // Các bảng trượt
  $('lopPhu').addEventListener('click', dongBang_);
  document.querySelectorAll('[data-dong]').forEach(function (b) {
    b.addEventListener('click', dongBang_);
  });

  // Bảng nhập
  $('nutThem').addEventListener('click', function () { moBangNhap_(null); });
  $('chonLoai').addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (b) chonLoaiNhap_(b.dataset.loai);
  });
  $('luoiHangMuc').addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (!b) return;
    trangThai.hmNhap = b.dataset.hm;
    veLuoiHangMuc_();
    $('loiNhap').hidden = true;
  });
  $('oSoTien').addEventListener('input', function () {
    dinhDangOTien_();
    $('loiNhap').hidden = true;
  });
  $('oSoTien').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') this.blur();
  });
  $('oGhiChu').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') luuBangNhap_();
  });
  $('nutBaSo').addEventListener('click', function () {
    const o = $('oSoTien');
    const so = o.value.replace(/\D/g, '');
    if (so && so.length <= 10) o.value = nhomSo_(Number(so + '000'));
  });
  $('nutLuu').addEventListener('click', luuBangNhap_);
  $('nutXoa').addEventListener('click', xoaKhoanDangSua_);

  // Tổng hợp
  $('nutAnSo').addEventListener('click', function () {
    duLieu.caiDat.anSo = !duLieu.caiDat.anSo;
    luuDuLieu_();
    veTongHop_();
  });
  $('chonCheDo').addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (!b) return;
    trangThai.cheDo = b.dataset.cheDo;
    veTongHop_();
  });
  document.querySelectorAll('.o-tong').forEach(function (o) {
    o.addEventListener('click', function () {
      trangThai.loaiXem = o.dataset.loai;
      trangThai.hmChon = '';
      veTongHop_();
    });
  });
  $('kyTruoc').addEventListener('click', function () {
    datKy_(trangThai.ky, dichMoc_(trangThai.ky, trangThai.moc, -1));
  });
  $('kySau').addEventListener('click', function () {
    datKy_(trangThai.ky, dichMoc_(trangThai.ky, trangThai.moc, 1));
  });
  $('nutKy').addEventListener('click', moChonKy_);
  $('dsKy').addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (b) datKy_(b.dataset.ky, new Date());
  });
  $('chonMoc').addEventListener('change', function () {
    if (MAU_TEN_NGAY.test(this.value)) datKy_('Ngày', tuChuoiNgay_(this.value));
  });
  $('dsHangMuc').addEventListener('click', function (e) {
    const b = e.target.closest('.dong-hm');
    if (b) moChiTiet_(b.dataset.hm);
  });

  // Cài đặt
  $('luoiMau').addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (b) doiMau_(b.dataset.ma);
  });
  $('nutXuatExcel').addEventListener('click', function () {
    if (!duLieu.giaoDich.length) { thongBao_('Chưa có khoản nào để xuất.'); return; }
    xuatExcel_();
  });
  $('nutXuatJson').addEventListener('click', function () {
    if (!duLieu.giaoDich.length) { thongBao_('Chưa có khoản nào để xuất.'); return; }
    xuatJson_();
  });
  $('nutNhap').addEventListener('click', function () {
    const o = $('chonFile');
    o.value = '';
    o.click();
  });
  $('chonFile').addEventListener('change', khiChonFile_);

  // Qua 0h thì sang trang ngày mới; đổi khổ màn hình thì vẽ lại biểu đồ
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) kiemTraQuaNgay_();
  });
  setInterval(kiemTraQuaNgay_, 30000);
  let henVe = null;
  let rongCu = window.innerWidth;
  window.addEventListener('resize', function () {
    if (window.innerWidth === rongCu) return;    // bàn phím bật tắt thì bỏ qua
    rongCu = window.innerWidth;
    clearTimeout(henVe);
    henVe = setTimeout(function () {
      if (trangThai.trang === 'trangTongHop') veTongHop_();
    }, 150);
  });
}

napDuLieu_();
apDungMau_(duLieu.caiDat.mau);
ganSuKien_();
chuyenTrang_('trangNgay');
