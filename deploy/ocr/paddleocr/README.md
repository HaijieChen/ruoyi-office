# PaddleOCR on 10.20.32.1

Listen 8099. OA yaml: yudao.finance.invoice-ocr.base-url: http://10.20.32.1:8099

Start a CPU PaddleOCR HTTP wrapper on this host (docker or venv). POST /ocr/invoice {"fileUrl":"..."} or {"fileBase64":"..."} -> {feeDate, amount, invoiceNo, taxAmount, invoiceType, buyerName, rawText}.

Parsers: VAT e-invoice, bank receipts, DiDi e-invoice, railway e-ticket, air itinerary. Air ticket no is 印刷序号 / SERIAL NUMBER, not 电子客票号码. Fee date prefers travel/first-segment date.

Excerpt tests (no original PDFs): `python3 test_parse_text.py` from this directory.
