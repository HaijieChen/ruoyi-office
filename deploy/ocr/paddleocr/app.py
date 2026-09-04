from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional
import os, re, tempfile, urllib.request, base64
from urllib.parse import urlsplit, urlunsplit, quote

app = FastAPI()
_ocr = None


def get_ocr():
    global _ocr
    if _ocr is None:
        from paddleocr import PaddleOCR
        try:
            _ocr = PaddleOCR(use_angle_cls=True, lang="ch", use_gpu=False, show_log=False)
        except TypeError:
            _ocr = PaddleOCR(lang="ch")
    return _ocr


class Req(BaseModel):
    fileUrl: Optional[str] = None
    fileBase64: Optional[str] = None


@app.get("/health")
def health():
    return {"ok": True}


def _norm_date(raw):
    if not raw:
        return None
    s = raw.replace("年", "-").replace("月", "-").replace("日", "").replace(".", "-").replace("/", "-")
    parts = [p for p in s.split("-") if p]
    if len(parts) < 3:
        return None
    try:
        return f"{int(parts[0]):04d}-{int(parts[1]):02d}-{int(parts[2]):02d}"
    except ValueError:
        return None


def _dates(joined):
    found = re.findall(r"((?:19|20)\d{2}[-./年]\d{1,2}[-./月]\d{1,2})", joined)
    out = []
    for d in found:
        n = _norm_date(d)
        if n:
            out.append(n)
    return out


def _invoice_nos(joined):
    return re.findall(r"发票号码\s*[:：]?\s*([0-9]{8,20})", joined)


def _serial_no(joined):
    m = re.search(
        r"(?:印刷序号|SERIAL\s*NUM(?:BER)?)\s*[:：]?\s*([0-9][0-9 ]{6,24}\d)",
        joined, re.I)
    if m:
        return re.sub(r"\s+", "", m.group(1))
    m = re.search(
        r"(?:印刷序号|SERIAL\s*NUM(?:BER)?).{0,80}?((?:\d[ \t]*){10,14})",
        joined, re.I)
    if m:
        digits = re.sub(r"\s+", "", m.group(1))
        if 10 <= len(digits) <= 14:
            return digits
    return None


def ticket_kind(joined):
    if re.search(r"航空运输电子客票行程单|ITINERARY.?RECEIPT OF E.?TICKET", joined, re.I):
        return "air"
    if re.search(r"电子发票（铁路电子客票）|铁路电子客票", joined):
        return "rail"
    if re.search(r"滴滴|电子发票（普通发票）|电子发.?[（(]普通发票[)）]", joined) and re.search(r"旅客运输|客运服", joined):
        return "didi"
    return "vat"


def parse_invoice_type(joined):
    kind = ticket_kind(joined)
    if kind == "air":
        return "其他"
    if kind in ("rail", "didi"):
        return "普票"
    if re.search(r"增值税专用发票|专用发票", joined):
        return "专票"
    if re.search(r"增值税普通发票|普通发票|电子发票（普通发票）", joined):
        return "普票"
    if "专票" in joined and "普票" not in joined:
        return "专票"
    if "普票" in joined:
        return "普票"
    return "其他"


def parse_tax_amount(joined):
    taxes = re.findall(r"税额[:：]?\s*[¥￥]?\s*(\d+\.\d{2})", joined)
    if taxes:
        return taxes[-1]
    return None


def parse_invoice_no(joined):
    serial = _serial_no(joined)
    if serial:
        return serial
    compact = re.sub(r"[\s　]", "", joined)
    nos = re.findall(r"发票号码[:：]?[^0-9]{0,8}([0-9]{8,20})", compact)
    if nos:
        return nos[0]
    nos = re.findall(r"(?<!\d)(\d{20})(?!\d)", joined)
    if not nos:
        nos = re.findall(r"(?<!\d)(\d{20})(?!\d)", compact)
    if nos:
        return nos[0]
    nos = re.findall(r"(?<!电子客票)号码[:：]([0-9]{8,20})", compact)
    return nos[0] if nos else None


def parse_amount(joined):
    compact = re.sub(r"[\s　]", "", joined)
    patterns = [
        r"价税合计(?:\(大写\)|（大写）)?[^0-9¥￥]{0,80}(?:\(小写\)|（小写）)?[¥￥]?((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
        r"(?:\(小写\)|（小写）)[¥￥]?((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
        r"[¥￥]((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
    ]
    for pattern in patterns:
        amounts = re.findall(pattern, compact)
        if amounts:
            return amounts[-1].replace(",", "")
    return parse_receipt_amount(joined)


def parse_receipt_amount(joined):
    compact = re.sub(r"[\s　]", "", joined)
    patterns = [
        r"交易金额(?:\(小写\)|（小写）)[:：]?(?:CNY)?((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
        r"金额(?:\(小写\)|（小写）)[:：]?(?:CNY)?((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
        r"小写金额[:：]?((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
        r"金额[:：]?[¥￥]?(?:CNY)?((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
        r"CNY((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
        r"[¥￥]((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})",
    ]
    for pattern in patterns:
        m = re.search(pattern, compact)
        if m:
            return m.group(1).replace(",", "")
    return None


def parse_receipt_serial(joined):
    compact = re.sub(r"[\s　]", "", joined)
    patterns = [
        r"交易流水号[:：]?([A-Za-z0-9-]{6,40})",
        r"交易流水[:：]?([A-Za-z0-9-]{6,40})",
        r"账户明细编号-交易流水号[:：]?([A-Za-z0-9-]{6,40})",
        r"核心流水号[:：]?([A-Za-z0-9-]{6,40})",
        r"会计流水号[:：]?([A-Za-z0-9-]{6,40})",
        r"电子回单号码[:：]?([A-Za-z0-9-]{6,40})",
        r"回单编号[:：]?([A-Za-z0-9-]{6,40})",
        r"凭证号[:：]?([A-Za-z0-9-]{6,40})",
    ]
    for pattern in patterns:
        m = re.search(pattern, compact)
        if m:
            return m.group(1)
    return None


def parse_receipt_date(joined):
    m = re.search(
        r"(?:记账日期|交易日期|交易时间|会计日期|时间戳|日期)[:：]?\s*(20\d{2}[-./年]\d{1,2}[-./月]\d{1,2}|20\d{6})",
        joined,
    )
    if m:
        return m.group(1)
    compact = re.sub(r"[\s　]", "", joined)
    m = re.search(
        r"(?:记账日期|交易日期|交易时间|会计日期|时间戳|日期)[:：]?(20\d{2}[-./年]\d{1,2}[-./月]\d{1,2}|20\d{6})",
        compact,
    )
    return m.group(1) if m else None


def parse_buyer_name(joined):
    compact = re.sub(r"[\s　]+", "", joined)
    patterns = [
        r"购买方(?:信息)?名称[:：]?([^销税]{2,80}?)(?:纳税人识别号|统一社会信用代码|销售方|$)",
        r"购货单位(?:名称)?[:：]?([^销税]{2,80}?)(?:纳税人识别号|统一社会信用代码|销售方|$)",
        r"购买方[^销]{0,40}名称[:：]?([^销税]{2,80}?)(?:纳税人识别号|统一社会信用代码|销售方|$)",
    ]
    for pattern in patterns:
        m = re.search(pattern, compact)
        if m:
            name = re.split(r"[，,。；;]|纳税人识别号|统一社会信用代码|销售方", m.group(1))[0].strip()
            if name:
                return name
    return None


def _parse_air(joined):
    no = _serial_no(joined)
    dates = _dates(joined)
    issue = None
    m = re.search(r"(?:填开日期|DATE OF ISSUE)\s*[:：]?\s*((?:19|20)\d{2}[-./年]\d{1,2}[-./月]\d{1,2})", joined, re.I)
    if m:
        issue = _norm_date(m.group(1))
    fee = next((d for d in dates if d != issue), None)
    amt = None
    m = re.search(r"(?:合计|TOTAL)(?:\s*TOTAL)?(?:\s*CNY)?\s*[¥￥]?\s*(\d+\.\d{2})", joined, re.I)
    if m:
        amt = m.group(1)
    else:
        m = re.search(r"CNY\s*(\d+\.\d{2})", joined, re.I)
        if m:
            amt = m.group(1)
    return fee, amt, no, None, "其他"


def _parse_rail(joined):
    nos = _invoice_nos(joined)
    no = nos[0] if nos else parse_invoice_no(joined)
    issue = None
    m = re.search(r"开票日期\s*[:：]?\s*((?:19|20)\d{2}[-./年]\d{1,2}[-./月]\d{1,2})", joined)
    if m:
        issue = _norm_date(m.group(1))
    dates = _dates(joined)
    fee = next((d for d in dates if d != issue), issue)
    amt = None
    m = re.search(r"票价[:：]?\s*[¥￥]?\s*(\d+\.\d{2})", joined)
    if m:
        amt = m.group(1)
    else:
        yen = re.findall(r"[¥￥]\s*(\d+\.\d{2})", joined)
        if yen:
            amt = yen[-1]
    return fee, amt, no, None, "普票"


def _parse_didi(joined):
    nos = _invoice_nos(joined)
    no = nos[0] if nos else parse_invoice_no(joined)
    travel = None
    m = re.search(r"出行日期\s*[:：]?\s*((?:19|20)\d{2}[-./年]\d{1,2}[-./月]\d{1,2})", joined)
    if m:
        travel = _norm_date(m.group(1))
    issue = None
    m = re.search(r"开票日期\s*[:：]?\s*((?:19|20)\d{2}[-./年]\d{1,2}[-./月]\d{1,2})", joined)
    if m:
        issue = _norm_date(m.group(1))
    fee = travel or issue
    amt = None
    m = re.search(r"价\s*税\s*合\s*计[^0-9¥￥]{0,40}[¥￥]?\s*(\d+\.\d{2})", joined)
    if m:
        amt = m.group(1)
    else:
        amt = parse_amount(joined)
    tax = parse_tax_amount(joined)
    if tax is None:
        m = re.search(r"合\s*计\s*[¥￥]?\s*(\d+\.\d{2})\s*[¥￥]?\s*(\d+\.\d{2})", joined)
        if m:
            tax = m.group(2)
    return fee, amt, no, tax, "普票"


def parse_text(joined):
    kind = ticket_kind(joined)
    if kind == "air":
        fee, amt, no, tax, inv = _parse_air(joined)
        return fee, amt, no, tax, inv, parse_buyer_name(joined), joined[:4000]
    if kind == "rail":
        fee, amt, no, tax, inv = _parse_rail(joined)
        return fee, amt, no, tax, inv, parse_buyer_name(joined), joined[:4000]
    if kind == "didi":
        fee, amt, no, tax, inv = _parse_didi(joined)
        return fee, amt, no, tax, inv, parse_buyer_name(joined), joined[:4000]
    dates = re.findall(r"(?:开票日期|日期)[:：]?\s*(20\d{2}[-./年]\d{1,2}[-./月]\d{1,2})", joined)
    if not dates:
        receipt_date = parse_receipt_date(joined)
        if receipt_date:
            dates = [receipt_date]
    if not dates:
        dates = re.findall(r"20\d{2}[-./年]\d{1,2}[-./月]\d{1,2}", joined)
    fee = None
    if dates:
        raw_date = dates[0]
        if re.fullmatch(r"20\d{6}", raw_date):
            fee = f"{raw_date[0:4]}-{raw_date[4:6]}-{raw_date[6:8]}"
        else:
            fee = raw_date.replace("年", "-").replace("月", "-").replace(".", "-").replace("/", "-")[:10]
    amt = parse_amount(joined)
    no = parse_invoice_no(joined) or parse_receipt_serial(joined)
    return fee, amt, no, parse_tax_amount(joined), parse_invoice_type(joined), parse_buyer_name(joined), joined[:4000]


def pdf_text(path):
    try:
        from pypdf import PdfReader
        reader = PdfReader(path)
        pages = "\n".join((p.extract_text() or "") for p in reader.pages)
        extra = []
        attachments = getattr(reader, "attachments", None) or {}
        for name, payload in attachments.items():
            if not str(name).lower().endswith(".xml"):
                continue
            blobs = payload if isinstance(payload, list) else [payload]
            for blob in blobs:
                if not isinstance(blob, bytes):
                    continue
                xml = blob.decode("utf-8", errors="ignore")
                if re.search(r"发票号码|票价|印刷序号|SERIAL", xml):
                    extra.append(xml)
        return pages + ("\n" + "\n".join(extra) if extra else "")
    except Exception:
        return ""


def _collect_ocr_texts(raw):
    texts = []
    if raw is None:
        return texts
    pages = raw if isinstance(raw, list) else [raw]
    for page in pages:
        if page is None:
            continue
        rec = None
        if isinstance(page, dict):
            rec = page.get("rec_texts") or page.get("rec_text")
        elif hasattr(page, "get"):
            rec = page.get("rec_texts") or page.get("rec_text")
        else:
            rec = getattr(page, "rec_texts", None)
        if rec:
            texts.extend(str(x) for x in rec)
            continue
        if isinstance(page, list):
            for line in page:
                if line and len(line) > 1:
                    item = line[1]
                    texts.append(str(item[0] if isinstance(item, (list, tuple)) else item))
    return texts


def ocr_image(path):
    engine = get_ocr()
    raw = None
    if hasattr(engine, "predict"):
        try:
            raw = engine.predict(path)
        except TypeError:
            raw = engine.ocr(path)
    else:
        try:
            raw = engine.ocr(path, cls=True)
        except TypeError:
            raw = engine.ocr(path)
    return " ".join(_collect_ocr_texts(raw))


def raster_pdf(path):
    try:
        import pymupdf
        doc = pymupdf.open(path)
        page = doc[0]
        pix = page.get_pixmap(matrix=pymupdf.Matrix(2, 2), alpha=False)
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".png")
        pix.save(tmp.name)
        tmp.close()
        return tmp.name
    except Exception:
        return None


def _usable(text):
    t = (text or "").strip()
    if len(t) < 20:
        return False
    return bool(ticket_kind(t) != "vat" or re.search(r"发票号码|价税合计|回单|交易流水", t))


def recognize_file(path):
    with open(path, "rb") as f:
        magic = f.read(5)
    if magic.startswith(b"%PDF"):
        t = pdf_text(path)
        if _usable(t):
            return parse_text(t)
        raster = raster_pdf(path)
        if raster:
            try:
                return parse_text(ocr_image(raster))
            finally:
                if os.path.exists(raster):
                    os.unlink(raster)
    return parse_text(ocr_image(path))


@app.post("/ocr/invoice")
def invoice(req: Req):
    tmp = None
    try:
        if req.fileBase64:
            raw = base64.b64decode(req.fileBase64)
            suf = ".pdf" if raw[:4] == b"%PDF" else ".jpg"
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suf)
            tmp.write(raw)
            tmp.close()
            path = tmp.name
        elif req.fileUrl and req.fileUrl.startswith(("http://", "https://")):
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".bin")
            tmp.close()
            parts = urlsplit(req.fileUrl)
            encoded = urlunsplit((parts.scheme, parts.netloc, quote(parts.path, safe="/"), parts.query, parts.fragment))
            urllib.request.urlretrieve(encoded, tmp.name)
            path = tmp.name
        else:
            return {"feeDate": None, "amount": None, "invoiceNo": None, "taxAmount": None,
                    "invoiceType": "其他", "buyerName": None, "rawText": "missing file"}
        fee, amt, no, tax, kind, buyer, joined = recognize_file(path)
        return {"feeDate": fee, "amount": amt, "invoiceNo": no, "taxAmount": tax,
                "invoiceType": kind, "buyerName": buyer, "rawText": joined}
    except Exception as e:
        return {"feeDate": None, "amount": None, "invoiceNo": None, "taxAmount": None,
                "invoiceType": "其他", "buyerName": None, "rawText": str(e)[:500]}
    finally:
        if tmp and os.path.exists(tmp.name):
            os.unlink(tmp.name)
