/**
 * SỔ CHI TIÊU - ứng dụng điện thoại
 * Chuyển từ bản Google Trang tính (phiên bản 8):
 *  - Trang ngày: tổng thu, tổng chi, số dư có dấu và các khoản trong ngày
 *  - Tổng hợp: xem theo ngày, tuần, tháng, năm; biểu đồ cột đôi và biểu đồ tròn
 *  - Sổ cái: toàn bộ các khoản, tìm theo hạng mục, ghi chú hoặc số tiền
 *  - Dữ liệu: sao lưu, khôi phục, xuất và nhập file bảng tính
 * Mọi khoản nằm chung một kho nên không thể đếm trùng giữa trang ngày và
 * sổ cái, cũng không cần dồn ngày cũ hay hẹn giờ lúc 0h.
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

/** Màu cột thu và chi trong biểu đồ cột đôi */
const MAU_COT = { Thu: '#1e7a3c', Chi: '#b03030' };

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
const SO_NGAY_MOI_LAN = 30;     // số ngày hiện thêm mỗi lần trong sổ cái

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

/** Chọn chữ trắng hoặc chữ đậm, cái nào tương phản với nền hơn */
function chuTrenNen_(hex) {
  const n = parseInt(hex.slice(1), 16);
  const kenh = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map(function (c) {
    c /= 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  const L = 0.2126 * kenh[0] + 0.7152 * kenh[1] + 0.0722 * kenh[2];
  return 1.05 / (L + 0.05) >= (L + 0.05) / 0.063 ? '#ffffff' : MAU_CHU;
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

function tenNgay_(d) {
  return THU_TRONG_TUAN[d.getDay()] + ', ' + dinhDang_(d);
}

function congNgay_(d, n) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
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

/** Đọc số tiền từ chữ: "50.000 đ", "50,000", "1.250,50" */
function docSoTien_(chu) {
  let s = String(chu == null ? '' : chu).replace(/[^\d.,-]/g, '');
  const am = s.indexOf('-') === 0;
  s = s.replace(/-/g, '');
  const thapPhan = s.match(/[.,](\d{1,2})$/);
  let so;
  if (thapPhan) {
    so = Number(s.slice(0, thapPhan.index).replace(/[.,]/g, '') + '.' + thapPhan[1]);
  } else {
    so = Number(s.replace(/[.,]/g, ''));
  }
  if (!isFinite(so)) return 0;
  return Math.round(am ? -so : so);
}

function boDau_(s) {
  return String(s).normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
}


/* ========== KHO DỮ LIỆU ========== */

let duLieu = { giaoDich: [] };

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
  } catch (e) {
    thongBao_('Không đọc được dữ liệu đã lưu.');
  }
}

function luuDuLieu_() {
  const ok = ghiKho_(JSON.stringify({ phienBan: 1, giaoDich: duLieu.giaoDich }));
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

/** Tính khoảng ngày cần xem */
function tinhKhoang_(ky, moc) {
  const d = new Date(moc.getFullYear(), moc.getMonth(), moc.getDate());
  let bd, kt, mo;

  if (ky === 'Tuần') {
    const lui = (d.getDay() + 6) % 7;            // đưa về thứ Hai
    bd = congNgay_(d, -lui);
    kt = congNgay_(bd, 6);
    mo = 'Tuần từ ' + dinhDang_(bd) + ' đến ' + dinhDang_(kt);
  } else if (ky === 'Tháng') {
    bd = new Date(d.getFullYear(), d.getMonth(), 1);
    kt = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    mo = 'Tháng ' + (d.getMonth() + 1) + '/' + d.getFullYear();
  } else if (ky === 'Năm') {
    bd = new Date(d.getFullYear(), 0, 1);
    kt = new Date(d.getFullYear(), 11, 31);
    mo = 'Năm ' + d.getFullYear();
  } else {
    bd = d; kt = d;
    mo = tenNgay_(d);
  }

  return { bd: chuoiNgay_(bd), kt: chuoiNgay_(kt), moTa: mo, dBD: bd, dKT: kt };
}

/** Các mốc trên trục ngang của biểu đồ cột */
function mocBieuDo_(ky, dBD, dKT) {
  if (ky === 'Tuần') {
    const ngan = [], dai = [], ngay = [];
    for (let i = 0; i < 7; i++) {
      const d = congNgay_(dBD, i);
      ngan.push(THU_NGAN[d.getDay()] + '\n' + hai_(d.getDate()) + '/' + hai_(d.getMonth() + 1));
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
  ky: 'Ngày',
  moc: new Date(),
  kieuBD: 'Cột đôi',
  timKiem: '',
  locLoai: 'Tất cả',
  soNgaySoCai: SO_NGAY_MOI_LAN,
  dangSua: null,
  loaiNhap: 'Chi',
  hmNhap: ''
};

function chonNhom_(idNhom, giaTri) {
  $(idNhom).querySelectorAll('button').forEach(function (b) {
    b.setAttribute('role', 'radio');
    b.setAttribute('aria-checked', String(b.dataset.giaTri === giaTri));
  });
}

function ganNhom_(idNhom, khiChon) {
  $(idNhom).addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (b) khiChon(b.dataset.giaTri);
  });
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
  $('nutThem').hidden = !(ten === 'trangNgay' || ten === 'trangSoCai');
  window.scrollTo(0, 0);
  veTrangHienTai_();
}

function veTrangHienTai_() {
  if (trangThai.trang === 'trangNgay') veTrangNgay_();
  else if (trangThai.trang === 'trangTongHop') veTongHop_();
  else if (trangThai.trang === 'trangSoCai') veSoCai_();
  else veDuLieu_();
}

function ghiTong_(idThu, idChi, idDu, idTheDu, t) {
  $(idThu).textContent = tien_(t.thu);
  $(idChi).textContent = tien_(t.chi);
  $(idDu).textContent = tienCoDau_(t.du);
  $(idTheDu).classList.toggle('duong', t.du > 0);
  $(idTheDu).classList.toggle('am', t.du < 0);
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

let traLoiXacNhan = null;

function xacNhan_(tieuDe, noiDung, nhanDongY, nguyHiem) {
  $('htTieuDe').textContent = tieuDe;
  $('htNoiDung').textContent = noiDung;
  $('htDongY').textContent = nhanDongY || 'Đồng ý';
  $('htDongY').classList.toggle('nguy-hiem', !!nguyHiem);
  $('hopThoai').hidden = false;
  $('lopPhu').hidden = false;
  $('lopPhu').classList.add('tren');
  $('htHuy').focus();
  return new Promise(function (xong) { traLoiXacNhan = xong; });
}

function dongXacNhan_(dongY) {
  $('hopThoai').hidden = true;
  $('lopPhu').classList.remove('tren');
  $('lopPhu').hidden = $('bangNhap').hidden;
  const xong = traLoiXacNhan;
  traLoiXacNhan = null;
  if (xong) xong(dongY);
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
  const d = tuChuoiNgay_(trangThai.ngay);
  $('nhanNgay').textContent = tenNgay_(d);
  $('chonNgay').value = trangThai.ngay;
  $('veHomNay').hidden = trangThai.ngay === homNay_();

  const ds = duLieu.giaoDich
    .filter(function (g) { return g.ngay === trangThai.ngay; })
    .sort(theoGio_);

  ghiTong_('ngayThu', 'ngayChi', 'ngayDu', 'theNgayDu', tongHop_(ds));

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
  trangThai.dangSua = g ? g.id : null;
  $('tieuDeNhap').textContent = g ? 'Sửa khoản' : 'Thêm khoản mới';
  chonLoaiNhap_(g ? g.loai : 'Chi');
  trangThai.hmNhap = g ? g.hm : '';
  veLuoiHangMuc_();
  $('oSoTien').value = g ? nhomSo_(g.tien) : '';
  $('oNgayNhap').value = g ? g.ngay
    : (trangThai.trang === 'trangNgay' ? trangThai.ngay : homNay_());
  // Giống trang tính: ghi khoản mới thì tự điền giờ hiện tại
  $('oGioNhap').value = g ? g.gio : gioHienTai_();
  $('oGhiChu').value = g ? g.ghiChu : '';
  $('loiNhap').hidden = true;
  $('nutXoa').hidden = !g;

  $('lopPhu').hidden = false;
  $('bangNhap').hidden = false;
  $('bangNhap').scrollTop = 0;
  if (!g) $('oSoTien').focus();
}

function dongBangNhap_() {
  $('bangNhap').hidden = true;
  $('lopPhu').hidden = true;
  trangThai.dangSua = null;
  if (document.activeElement) document.activeElement.blur();
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
  dongBangNhap_();

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
  dongBangNhap_();
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

function veTongHop_() {
  chonNhom_('chonKy', trangThai.ky);
  chonNhom_('chonBieuDo', trangThai.kieuBD);

  const kh = tinhKhoang_(trangThai.ky, trangThai.moc);
  // Chọn Tuần thì kéo mốc về đúng thứ Hai cho khớp
  if (trangThai.ky === 'Tuần') trangThai.moc = kh.dBD;

  $('nhanMoc').textContent = dinhDang_(trangThai.moc);
  $('chonMoc').value = chuoiNgay_(trangThai.moc);
  $('moTaKy').textContent = kh.moTa;

  const ds = duLieu.giaoDich.filter(function (g) {
    return g.ngay >= kh.bd && g.ngay <= kh.kt;
  });
  const t = tongHop_(ds);
  ghiTong_('thThu', 'thChi', 'thDu', 'theThDu', t);

  // --- Bảng theo hạng mục, mỗi hạng mục một màu ---
  const tenHM = Object.keys(t.theoHM).sort(function (a, b) {
    return t.theoHM[b].chi - t.theoHM[a].chi || t.theoHM[b].thu - t.theoHM[a].thu;
  });
  const bang = $('dsHangMuc');
  bang.textContent = '';
  tenHM.forEach(function (k) {
    const hang = tao_('tr');
    const o = tao_('td');
    const ten = tao_('span', 'ten-hm', k);
    ten.style.background = mauHM_(k);
    ten.style.color = chuTrenNen_(mauHM_(k));
    o.appendChild(ten);
    hang.appendChild(o);
    hang.appendChild(tao_('td', 'so-thu', tien_(t.theoHM[k].thu)));
    hang.appendChild(tao_('td', 'so-chi', tien_(t.theoHM[k].chi)));
    bang.appendChild(hang);
  });
  $('bangHangMuc').hidden = tenHM.length === 0;
  $('trongHangMuc').hidden = tenHM.length > 0;

  // --- Biểu đồ ---
  if (trangThai.kieuBD.indexOf('Tròn') === 0) {
    const layThu = trangThai.kieuBD === 'Tròn thu';
    const dong = tenHM
      .map(function (k) {
        return { ten: k, giaTri: layThu ? t.theoHM[k].thu : t.theoHM[k].chi, mau: mauHM_(k) };
      })
      .filter(function (x) { return x.giaTri > 0; })
      .sort(function (a, b) { return b.giaTri - a.giaTri; });
    $('tieuDeBD').textContent = (layThu ? 'Thu' : 'Chi') + ' theo hạng mục';
    veBieuDoTron_(dong, layThu ? 'Tổng thu' : 'Tổng chi');
  } else {
    const moc = mocBieuDo_(trangThai.ky, kh.dBD, kh.dKT);
    const thu = new Array(moc.ngan.length).fill(0);
    const chi = new Array(moc.ngan.length).fill(0);
    ds.forEach(function (g) {
      const i = moc.chiSo(g);
      if (i < 0 || i >= moc.ngan.length) return;
      if (g.loai === 'Thu') thu[i] += g.tien; else chi[i] += g.tien;
    });
    $('tieuDeBD').textContent = 'Thu và chi theo ' + moc.donVi;
    veBieuDoCot_(moc, thu, chi);
  }
}

function dichMoc_(buoc) {
  const m = trangThai.moc;
  if (trangThai.ky === 'Tuần') trangThai.moc = congNgay_(m, 7 * buoc);
  else if (trangThai.ky === 'Tháng') trangThai.moc = new Date(m.getFullYear(), m.getMonth() + buoc, 1);
  else if (trangThai.ky === 'Năm') trangThai.moc = new Date(m.getFullYear() + buoc, 0, 1);
  else trangThai.moc = congNgay_(m, buoc);
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

function veBieuDoCot_(moc, thu, chi) {
  const khung = $('khungBD');
  khung.textContent = '';
  $('bangSoBD').textContent = '';

  const chuGiai = $('chuGiaiBD');
  chuGiai.textContent = '';
  ['Thu', 'Chi'].forEach(function (loai) {
    const s = tao_('span');
    s.appendChild(oMau_(MAU_COT[loai]));
    s.appendChild(document.createTextNode(loai));
    chuGiai.appendChild(s);
  });

  const docSo = $('docSoBD');
  const goiY = function () {
    docSo.textContent = 'Chạm hoặc kéo ngang trên biểu đồ để xem số của từng '
      + moc.donVi + '.';
  };
  goiY();

  const n = moc.ngan.length;
  const W = Math.max(khung.clientWidth, 260);
  const coHaiDong = moc.ngan[0].indexOf('\n') >= 0;
  const trai = 40, phai = 4, tren = 8, duoi = coHaiDong ? 34 : 22;
  const H = 210 + duoi;
  const rongVe = W - trai - phai;
  const caoVe = H - tren - duoi;
  const lonNhat = Math.max.apply(null, thu.concat(chi));
  const vach = vachDep_(Math.max(lonNhat, 1000), 4);
  const yCua = function (v) { return tren + caoVe - v / vach.dinh * caoVe; };

  const s = svg_('svg', {
    viewBox: '0 0 ' + W + ' ' + H, width: W, height: H,
    role: 'img', 'aria-label': 'Biểu đồ cột thu và chi theo ' + moc.donVi
  }, khung);

  // Lưới ngang và nhãn trục dọc
  for (let v = 0; v <= vach.dinh + 1e-6; v += vach.buoc) {
    const y = Math.round(yCua(v)) + 0.5;
    svg_('line', {
      x1: trai, x2: W - phai, y1: y, y2: y,
      stroke: v === 0 ? MAU_TRUC : MAU_LUOI, 'stroke-width': 1
    }, s);
    const nhan = svg_('text', {
      x: trai - 6, y: y + 3.5, 'text-anchor': 'end',
      'font-size': 10, fill: MAU_CHU_MO
    }, s);
    nhan.textContent = tienGon_(v);
  }

  const oRong = rongVe / n;
  const khe = oRong < 14 ? 1 : 2;                        // khe trắng giữa hai cột
  const rongCot = Math.max(1.5, Math.min(24, (oRong * 0.78 - khe) / 2));

  const nen = svg_('rect', {
    x: 0, y: tren, width: oRong, height: caoVe, fill: '#dfe6f3', rx: 3, opacity: 0
  }, s);

  for (let i = 0; i < n; i++) {
    const giua = trai + oRong * (i + 0.5);
    [[thu[i], MAU_COT.Thu, giua - khe / 2 - rongCot], [chi[i], MAU_COT.Chi, giua + khe / 2]]
      .forEach(function (c) {
        if (c[0] <= 0) return;
        const h = Math.max(1.5, caoVe * c[0] / vach.dinh);
        svg_('path', { d: duongCot_(c[2], tren + caoVe - h, rongCot, h), fill: c[1] }, s);
      });
  }

  // Nhãn trục ngang, thưa bớt khi không đủ chỗ
  const dai = Math.max.apply(null, moc.ngan.map(function (x) {
    return Math.max.apply(null, x.split('\n').map(function (d) { return d.length; }));
  }));
  const buocNhan = Math.max(1, Math.ceil(n / Math.floor(rongVe / (dai * 6.2 + 6))));
  for (let i = 0; i < n; i += buocNhan) {
    const t = svg_('text', {
      x: trai + oRong * (i + 0.5), y: tren + caoVe + 14,
      'text-anchor': 'middle', 'font-size': 10, fill: MAU_CHU_MO
    }, s);
    moc.ngan[i].split('\n').forEach(function (dong, k) {
      const ts = svg_('tspan', { x: trai + oRong * (i + 0.5), dy: k ? 12 : 0 }, t);
      ts.textContent = dong;
    });
  }

  if (lonNhat <= 0) {
    const t = svg_('text', {
      x: trai + rongVe / 2, y: tren + caoVe / 2, 'text-anchor': 'middle',
      'font-size': 13, fill: MAU_CHU_MO
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
  if (coSo.length) {
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
    $('bangSoBD').appendChild(chiTiet);
  }
}

function cungTron_(cx, cy, R, r, a0, a1) {
  const lon = a1 - a0 > Math.PI ? 1 : 0;
  const d = function (ban, a) {
    return (cx + ban * Math.cos(a)).toFixed(2) + ',' + (cy + ban * Math.sin(a)).toFixed(2);
  };
  return 'M' + d(R, a0) + 'A' + R + ',' + R + ' 0 ' + lon + ' 1 ' + d(R, a1)
    + 'L' + d(r, a1) + 'A' + r + ',' + r + ' 0 ' + lon + ' 0 ' + d(r, a0) + 'Z';
}

function veBieuDoTron_(dong, nhanTong) {
  const khung = $('khungBD');
  khung.textContent = '';
  $('chuGiaiBD').textContent = '';
  $('bangSoBD').textContent = '';

  const tong = dong.reduce(function (a, x) { return a + x.giaTri; }, 0);
  const docSo = $('docSoBD');
  docSo.textContent = tong > 0 ? 'Chạm vào một phần của vòng tròn hoặc một dòng bên dưới để xem chi tiết.' : '';
  docSo.hidden = tong <= 0;

  const W = Math.min(Math.max(khung.clientWidth, 220), 280);
  const R = W / 2 - 2, r = R * 0.6, cx = W / 2, cy = W / 2;
  const s = svg_('svg', {
    viewBox: '0 0 ' + W + ' ' + W, width: W, height: W,
    role: 'img', 'aria-label': 'Biểu đồ tròn ' + nhanTong.toLowerCase() + ' theo hạng mục'
  }, khung);
  s.style.margin = '0 auto';
  s.style.width = W + 'px';

  const giua1 = svg_('text', {
    x: cx, y: cy - 6, 'text-anchor': 'middle', 'font-size': 12, fill: MAU_CHU_MO
  }, s);
  const giua2 = svg_('text', {
    x: cx, y: cy + 14, 'text-anchor': 'middle', 'font-size': 16, 'font-weight': 'bold', fill: MAU_CHU
  }, s);
  const ghiGiua = function (a, b) { giua1.textContent = a; giua2.textContent = b; };

  if (tong <= 0) {
    svg_('circle', {
      cx: cx, cy: cy, r: (R + r) / 2, fill: 'none', stroke: MAU_LUOI, 'stroke-width': R - r
    }, s);
    ghiGiua(nhanTong, 'Chưa có số liệu');
    return;
  }

  const phanTram = function (v) {
    const p = v / tong * 100;
    return (p < 10 ? String(Math.round(p * 10) / 10).replace('.', ',') : String(Math.round(p))) + '%';
  };

  const cacLat = [];
  const cacDong = [];
  let dangChon = -1;
  const chon = function (i) {
    dangChon = i === dangChon ? -1 : i;
    cacLat.forEach(function (lat, k) {
      lat.setAttribute('opacity', dangChon < 0 || k === dangChon ? 1 : 0.3);
    });
    cacDong.forEach(function (b, k) {
      b.classList.toggle('dang-chon', k === dangChon);
      b.classList.toggle('mo-di', dangChon >= 0 && k !== dangChon);
    });
    if (dangChon < 0) ghiGiua(nhanTong, tien_(tong));
    else ghiGiua(dong[dangChon].ten + ' · ' + phanTram(dong[dangChon].giaTri), tien_(dong[dangChon].giaTri));
  };

  let goc = -Math.PI / 2;
  dong.forEach(function (x, i) {
    const cung = x.giaTri / tong * Math.PI * 2;
    const lat = svg_('g', { class: 'lat' }, s);
    lat.style.cursor = 'pointer';
    if (dong.length === 1) {
      svg_('circle', {
        cx: cx, cy: cy, r: (R + r) / 2, fill: 'none', stroke: x.mau, 'stroke-width': R - r
      }, lat);
    } else {
      svg_('path', {
        d: cungTron_(cx, cy, R, r, goc, goc + cung),
        fill: x.mau, stroke: '#ffffff', 'stroke-width': 2, 'stroke-linejoin': 'round'
      }, lat);
    }
    lat.addEventListener('click', function () { chon(i); });
    cacLat.push(lat);

    // Ghi phần trăm ngay trên lát đủ lớn
    if (cung >= 0.38) {
      const giua = goc + cung / 2;
      const t = svg_('text', {
        x: cx + (R + r) / 2 * Math.cos(giua), y: cy + (R + r) / 2 * Math.sin(giua) + 4,
        'text-anchor': 'middle', 'font-size': 11, 'font-weight': 'bold',
        fill: chuTrenNen_(x.mau), 'pointer-events': 'none'
      }, lat);
      t.textContent = phanTram(x.giaTri);
    }
    goc += cung;
  });
  // Đưa chữ ở giữa lên trên cùng
  s.appendChild(giua1);
  s.appendChild(giua2);
  ghiGiua(nhanTong, tien_(tong));

  const chuGiai = tao_('div', 'chu-giai-tron');
  dong.forEach(function (x, i) {
    const b = tao_('button', 'dong-chu-giai');
    b.type = 'button';
    b.appendChild(oMau_(x.mau));
    b.appendChild(tao_('span', 'ten', x.ten));
    b.appendChild(tao_('span', 'tien', tien_(x.giaTri)));
    b.appendChild(tao_('span', 'phan-tram', phanTram(x.giaTri)));
    b.addEventListener('click', function () { chon(i); });
    cacDong.push(b);
    chuGiai.appendChild(b);
  });
  $('bangSoBD').appendChild(chuGiai);
}


/* ========== SỔ CÁI ========== */

function khopTimKiem_(g, tuKhoa) {
  if (!tuKhoa) return true;
  const chuSo = tuKhoa.replace(/[.\s]/g, '');
  if (/^\d+$/.test(chuSo) && String(g.tien).indexOf(chuSo) >= 0) return true;
  return boDau_(g.hm + ' ' + g.ghiChu).indexOf(tuKhoa) >= 0;
}

function veSoCai_() {
  chonNhom_('locLoai', trangThai.locLoai);
  const tuKhoa = boDau_(trangThai.timKiem.trim());
  const loc = trangThai.locLoai;

  const ds = duLieu.giaoDich.filter(function (g) {
    return (loc === 'Tất cả' || g.loai === loc) && khopTimKiem_(g, tuKhoa);
  });

  const theoNgay = {};
  ds.forEach(function (g) { (theoNgay[g.ngay] = theoNgay[g.ngay] || []).push(g); });
  const cacNgay = Object.keys(theoNgay).sort().reverse();

  const t = tongHop_(ds);
  const tomTat = $('tomTatSoCai');
  tomTat.textContent = '';
  if (ds.length) {
    tomTat.appendChild(tao_('span', null, nhomSo_(ds.length) + ' khoản trong ' + cacNgay.length + ' ngày'));
    tomTat.appendChild(tao_('span', null, 'Thu ' + tien_(t.thu) + ' · Chi ' + tien_(t.chi)));
  }

  const hop = $('dsSoCai');
  hop.textContent = '';
  cacNgay.slice(0, trangThai.soNgaySoCai).forEach(function (ngay) {
    const cacKhoan = theoNgay[ngay].sort(theoGio_);
    const tNgay = tongHop_(cacKhoan);

    const nhom = tao_('div', 'nhom-ngay');
    const dau = tao_('button', 'dau-nhom');
    dau.type = 'button';
    dau.dataset.ngay = ngay;
    dau.appendChild(tao_('span', null, tenNgay_(tuChuoiNgay_(ngay))));
    const du = tao_('span', 'du-nhom' + (tNgay.du > 0 ? ' duong' : tNgay.du < 0 ? ' am' : ''),
      tienCoDau_(tNgay.du));
    dau.appendChild(du);
    dau.setAttribute('aria-label', 'Mở trang ngày ' + tenNgay_(tuChuoiNgay_(ngay))
      + ', số dư ' + tienCoDau_(tNgay.du));
    nhom.appendChild(dau);

    const ol = tao_('ol', 'ds-giao-dich');
    cacKhoan.forEach(function (g, i) { ol.appendChild(taoDongGiaoDich_(g, i + 1)); });
    nhom.appendChild(ol);
    hop.appendChild(nhom);
  });

  const conLai = cacNgay.length - trangThai.soNgaySoCai;
  $('xemThem').hidden = conLai <= 0;
  $('xemThem').textContent = 'Xem thêm ' + Math.min(conLai, SO_NGAY_MOI_LAN) + ' ngày';

  const trong = $('trongSoCai');
  trong.hidden = ds.length > 0;
  trong.textContent = duLieu.giaoDich.length
    ? 'Không có khoản nào khớp với điều kiện tìm.'
    : 'Sổ cái còn trống. Các khoản ghi ở trang ngày sẽ hiện ở đây.';
}


/* ========== DỮ LIỆU: SAO LƯU, KHÔI PHỤC, XUẤT, NHẬP ========== */

function veDuLieu_() {
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

function taiFile_(ten, loai, noiDung) {
  if (coCauNoi_() && window.Android.luuFile) {
    window.Android.luuFile(ten, loai, noiDung);
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

function saoLuu_() {
  const noiDung = JSON.stringify({
    ungDung: 'so-chi-tieu',
    phienBan: 1,
    xuatLuc: new Date().toISOString(),
    giaoDich: duLieu.giaoDich
  }, null, 1);
  taiFile_('so-chi-tieu-sao-luu-' + homNay_() + '.json', 'application/json', noiDung);
}

function oCSV_(v) {
  const s = String(v == null ? '' : v);
  return /[",;\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}

function xuatCSV_() {
  const dong = [['NGÀY', 'GIỜ', 'LOẠI', 'HẠNG MỤC', 'SỐ TIỀN', 'GHI CHÚ']];
  duLieu.giaoDich.slice()
    .sort(function (a, b) { return a.ngay < b.ngay ? -1 : a.ngay > b.ngay ? 1 : theoGio_(a, b); })
    .forEach(function (g) { dong.push([g.ngay, g.gio, g.loai, g.hm, g.tien, g.ghiChu]); });
  const noiDung = '﻿' + dong.map(function (d) { return d.map(oCSV_).join(','); }).join('\r\n');
  taiFile_('so-chi-tieu-' + homNay_() + '.csv', 'text/csv', noiDung);
}

/** Tách chữ CSV thành bảng, tự nhận dấu phẩy, chấm phẩy hoặc tab */
function tachCSV_(chu) {
  chu = chu.replace(/^﻿/, '');
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

function docNgay_(v) {
  const s = String(v || '').trim();
  let m = s.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/);
  if (m) return m[1] + '-' + hai_(Number(m[2])) + '-' + hai_(Number(m[3]));
  m = s.match(/(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})/);
  if (m) return m[3] + '-' + hai_(Number(m[2])) + '-' + hai_(Number(m[1]));
  return '';
}

function docGioCSV_(v) {
  const s = String(v || '').trim();
  const m = s.match(/^(\d{1,2}):(\d{2})/);
  if (m && Number(m[1]) < 24) return hai_(Number(m[1])) + ':' + m[2];
  const n = Number(s.replace(',', '.'));
  if (s && !isNaN(n) && n > 0 && n < 1) {
    const phut = Math.round(n * 1440);
    return hai_(Math.floor(phut / 60) % 24) + ':' + hai_(phut % 60);
  }
  return '';
}

/**
 * Đọc các khoản trong file CSV. Nhận cả trang SO_CAI (có cột NGÀY) lẫn
 * trang ngày của bản trang tính (không có cột NGÀY, lấy ngày từ tên file
 * hoặc từ dòng tiêu đề "Thứ Hai, 22/09/2026").
 */
function docGiaoDichCSV_(chu, tenFile) {
  const bang = tachCSV_(chu);
  const chuan = function (x) { return boDau_(x).toUpperCase().replace(/\s+/g, ' ').trim(); };

  let hangDau = -1;
  for (let i = 0; i < Math.min(bang.length, 30); i++) {
    if (bang[i].some(function (x) { return chuan(x) === 'HANG MUC'; })) { hangDau = i; break; }
  }
  if (hangDau < 0) throw new Error('Không thấy cột HẠNG MỤC trong file.');

  const cot = {};
  bang[hangDau].forEach(function (x, i) {
    const ten = chuan(x);
    if (!(ten in cot)) cot[ten] = i;
  });
  if (!('SO TIEN' in cot)) throw new Error('Không thấy cột SỐ TIỀN trong file.');

  let ngayChung = '';
  if (!('NGAY' in cot)) {
    const m = String(tenFile || '').match(/\d{4}-\d{2}-\d{2}/);
    if (m) ngayChung = m[0];
    for (let i = 0; !ngayChung && i < hangDau; i++) {
      for (let k = 0; !ngayChung && k < bang[i].length; k++) ngayChung = docNgay_(bang[i][k]);
    }
    if (!ngayChung) throw new Error('Không biết các khoản trong file thuộc ngày nào.');
  }

  const lay = function (dong, ten) { return ten in cot ? dong[cot[ten]] : ''; };
  const ket = [];
  for (let i = hangDau + 1; i < bang.length; i++) {
    const dong = bang[i];
    const hm = String(lay(dong, 'HANG MUC') || '').trim();
    if (!hm) continue;
    const ngay = ngayChung || docNgay_(lay(dong, 'NGAY'));
    if (!ngay) continue;
    const tien = docSoTien_(lay(dong, 'SO TIEN'));
    const loaiGoc = chuan(lay(dong, 'LOAI'));
    const g = chuanHoa_({
      ngay: ngay,
      gio: docGioCSV_(lay(dong, 'GIO')),
      loai: loaiGoc.indexOf('THU') === 0 ? 'Thu' : 'Chi',
      hm: hm,
      tien: Math.abs(tien),
      ghiChu: lay(dong, 'GHI CHU'),
      tao: Date.now() + i
    });
    if (g) ket.push(g);
  }
  return ket;
}

function khoaTrung_(g) {
  return [g.ngay, g.gio, g.loai, g.hm, g.tien, g.ghiChu].join('|');
}

function nhapCSV_(chu, tenFile) {
  let moi;
  try {
    moi = docGiaoDichCSV_(chu, tenFile);
  } catch (e) {
    thongBao_(e.message);
    return;
  }
  if (!moi.length) { thongBao_('File không có khoản nào để nhập.'); return; }

  // Chống đếm trùng: khoản giống hệt khoản đã có thì bỏ qua
  const daCo = {};
  duLieu.giaoDich.forEach(function (g) {
    const k = khoaTrung_(g);
    daCo[k] = (daCo[k] || 0) + 1;
  });
  const them = [];
  moi.forEach(function (g) {
    const k = khoaTrung_(g);
    if (daCo[k]) { daCo[k]--; return; }
    them.push(g);
  });

  if (!them.length) {
    thongBao_('Tất cả ' + moi.length + ' khoản trong file đã có sẵn, không thêm gì.');
    return;
  }
  const truoc = duLieu.giaoDich.slice();
  duLieu.giaoDich = duLieu.giaoDich.concat(them);
  luuDuLieu_();
  veTrangHienTai_();
  thongBao_('Đã thêm ' + them.length + ' khoản'
    + (moi.length > them.length ? ', bỏ qua ' + (moi.length - them.length) + ' khoản trùng' : '') + '.', {
    nhan: 'Hoàn tác',
    lam: function () { duLieu.giaoDich = truoc; luuDuLieu_(); veTrangHienTai_(); }
  });
}

function khoiPhuc_(chu) {
  let ds;
  try {
    const obj = JSON.parse(chu.replace(/^﻿/, ''));
    ds = Array.isArray(obj) ? obj : obj && obj.giaoDich;
    if (!Array.isArray(ds)) throw new Error();
  } catch (e) {
    thongBao_('File này không phải file sao lưu của Sổ chi tiêu.');
    return;
  }
  const moi = chuanHoaDanhSach_(ds);
  xacNhan_('Khôi phục dữ liệu?',
    'Toàn bộ ' + nhomSo_(duLieu.giaoDich.length) + ' khoản hiện có sẽ được thay bằng '
    + nhomSo_(moi.length) + ' khoản trong file sao lưu.', 'Khôi phục', true)
    .then(function (dongY) {
      if (!dongY) return;
      const truoc = duLieu.giaoDich;
      duLieu.giaoDich = moi;
      luuDuLieu_();
      veTrangHienTai_();
      thongBao_('Đã khôi phục ' + nhomSo_(moi.length) + ' khoản.', {
        nhan: 'Hoàn tác',
        lam: function () { duLieu.giaoDich = truoc; luuDuLieu_(); veTrangHienTai_(); }
      });
    });
}

function chonFile_(muc) {
  const o = $('chonFile');
  o.dataset.muc = muc;
  o.value = '';
  o.click();
}

function khiChonFile_() {
  const o = $('chonFile');
  const f = o.files && o.files[0];
  if (!f) return;
  const doc = new FileReader();
  doc.onload = function () {
    if (o.dataset.muc === 'khoiPhuc') khoiPhuc_(String(doc.result));
    else nhapCSV_(String(doc.result), f.name);
  };
  doc.onerror = function () { thongBao_('Không đọc được file.'); };
  doc.readAsText(f, 'utf-8');
}


/* ========== NÚT QUAY LẠI CỦA ANDROID ========== */

/** Trả về true nếu đã tự xử lý, false để Android đóng app */
window.xuLyQuayLai = function () {
  if (!$('hopThoai').hidden) { dongXacNhan_(false); return true; }
  if (!$('bangNhap').hidden) { dongBangNhap_(); return true; }
  if (trangThai.trang !== 'trangNgay') { chuyenTrang_('trangNgay'); return true; }
  return false;
};


/* ========== KHỞI ĐỘNG ========== */

function ganSuKien_() {
  document.querySelector('.thanh-duoi').addEventListener('click', function (e) {
    const b = e.target.closest('button');
    if (b) chuyenTrang_(b.dataset.trang);
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
  $('veHomNay').addEventListener('click', function () { denNgay_(homNay_()); });

  // Bấm vào một khoản (ở trang ngày hoặc sổ cái) để sửa; bấm đầu nhóm để mở trang ngày
  document.querySelector('main').addEventListener('click', function (e) {
    const dong = e.target.closest('.dong');
    if (dong) {
      const g = duLieu.giaoDich.find(function (x) { return x.id === dong.dataset.id; });
      if (g) moBangNhap_(g);
      return;
    }
    const dau = e.target.closest('.dau-nhom');
    if (dau) denNgay_(dau.dataset.ngay);
  });

  // Bảng nhập
  $('nutThem').addEventListener('click', function () { moBangNhap_(null); });
  $('dongNhap').addEventListener('click', dongBangNhap_);
  $('lopPhu').addEventListener('click', function () {
    if (!$('hopThoai').hidden) dongXacNhan_(false);
    else dongBangNhap_();
  });
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

  $('htHuy').addEventListener('click', function () { dongXacNhan_(false); });
  $('htDongY').addEventListener('click', function () { dongXacNhan_(true); });

  // Tổng hợp
  ganNhom_('chonKy', function (v) { trangThai.ky = v; veTongHop_(); });
  ganNhom_('chonBieuDo', function (v) { trangThai.kieuBD = v; veTongHop_(); });
  $('mocTruoc').addEventListener('click', function () { dichMoc_(-1); });
  $('mocSau').addEventListener('click', function () { dichMoc_(1); });
  $('chonMoc').addEventListener('change', function () {
    if (!MAU_TEN_NGAY.test(this.value)) return;
    trangThai.moc = tuChuoiNgay_(this.value);
    veTongHop_();
  });

  // Sổ cái
  let henTim = null;
  $('oTim').addEventListener('input', function () {
    const o = this;
    clearTimeout(henTim);
    henTim = setTimeout(function () {
      trangThai.timKiem = o.value;
      trangThai.soNgaySoCai = SO_NGAY_MOI_LAN;
      veSoCai_();
    }, 150);
  });
  ganNhom_('locLoai', function (v) {
    trangThai.locLoai = v;
    trangThai.soNgaySoCai = SO_NGAY_MOI_LAN;
    veSoCai_();
  });
  $('xemThem').addEventListener('click', function () {
    trangThai.soNgaySoCai += SO_NGAY_MOI_LAN;
    veSoCai_();
  });

  // Dữ liệu
  $('nutSaoLuu').addEventListener('click', saoLuu_);
  $('nutKhoiPhuc').addEventListener('click', function () { chonFile_('khoiPhuc'); });
  $('nutXuatCSV').addEventListener('click', function () {
    if (!duLieu.giaoDich.length) { thongBao_('Chưa có khoản nào để xuất.'); return; }
    xuatCSV_();
  });
  $('nutNhapCSV').addEventListener('click', function () { chonFile_('nhapCSV'); });
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
ganSuKien_();
chuyenTrang_('trangNgay');
