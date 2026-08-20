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
    return fee, amt, joined[:2000]


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
            return {"feeDate": None, "amount": None, "rawText": "missing file"}
        fee, amt, joined = recognize_file(path)
        return {"feeDate": fee, "amount": amt, "rawText": joined}
    except Exception as e:
        return {"feeDate": None, "amount": None, "rawText": str(e)[:500]}
    finally:
        if tmp and os.path.exists(tmp.name):
            os.unlink(tmp.name)
