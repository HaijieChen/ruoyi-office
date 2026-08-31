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
        _ocr = PaddleOCR(use_angle_cls=True, lang="ch", use_gpu=False, show_log=False)
    return _ocr


class Req(BaseModel):
    fileUrl: Optional[str] = None
    fileBase64: Optional[str] = None


@app.get("/health")
def health():
    return {"ok": True}


def parse_invoice_type(joined):
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
    compact = re.sub(r"[\s　]", "", joined)
    nos = re.findall(r"发票号码[:：]?([0-9]{8,20})", compact)
    if nos:
        return nos[0]
    nos = re.findall(r"(?<!\d)(20\d{18})(?!\d)", compact)
    if nos:
        return nos[0]
    nos = re.findall(r"号码[:：]([0-9]{8,20})", compact)
    return nos[0] if nos else None


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


def parse_text(joined):
    dates = re.findall(r"20\d{2}[-./年]\d{1,2}[-./月]\d{1,2}", joined)
    amounts = re.findall(r"(?:价税合计[^¥]{0,40}[¥￥]?|（小写）\s*[¥￥]?)(\d+\.\d{2})", joined)
    if not amounts:
        amounts = re.findall(r"[¥￥](\d+\.\d{2})", joined)
    if not amounts:
        amounts = re.findall(r"(\d{1,7}\.\d{2})", joined)
    fee = None
    if dates:
        fee = dates[0].replace("年", "-").replace("月", "-").replace(".", "-").replace("/", "-")[:10]
    amt = amounts[-1] if amounts else None
    no = parse_invoice_no(joined)
    return fee, amt, no, parse_tax_amount(joined), parse_invoice_type(joined), parse_buyer_name(joined), joined[:2000]


def pdf_text(path):
    try:
        from pypdf import PdfReader
        reader = PdfReader(path)
        return "\n".join((p.extract_text() or "") for p in reader.pages)
    except Exception:
        return ""


def ocr_image(path):
    raw = get_ocr().ocr(path, cls=True)
    texts = []
    for page in raw or []:
        for line in page or []:
            if line and len(line) > 1:
                texts.append(str(line[1][0]))
    return " ".join(texts)


def recognize_file(path):
    with open(path, "rb") as f:
        magic = f.read(5)
    if magic.startswith(b"%PDF"):
        t = pdf_text(path)
        if len(t.strip()) >= 20:
            return parse_text(t)
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
