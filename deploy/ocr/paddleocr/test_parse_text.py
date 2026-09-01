"""Excerpt tests for travel-ticket parse_text. No original PDFs."""
import sys
import types
import unittest

fastapi = types.ModuleType("fastapi")


class _App:
    def get(self, *a, **k):
        def deco(fn):
            return fn
        return deco

    def post(self, *a, **k):
        def deco(fn):
            return fn
        return deco


fastapi.FastAPI = lambda *a, **k: _App()
sys.modules.setdefault("fastapi", fastapi)
pydantic = types.ModuleType("pydantic")
class BaseModel:
    pass
pydantic.BaseModel = BaseModel
sys.modules.setdefault("pydantic", pydantic)

from app import parse_text

DIDI = """
电子发票（普通发票）
旅客运输服务 发票号码 : 26317000003076038180
开票日期 : 2026年08月25日
名称：购买方已脱敏
合 计 1176.48¥ 35.29¥
价 税 合 计 （ 大 写 ） （ 小 写 ） 1211.77¥
"""

RAIL = """
开票日期:2026年08月20日
发票号码:26339190041009328118
2026年08月18日
14:40开
票价:
二等座
电子发票（铁路电子客票）
￥77.00
"""

AIR = """
航空运输电子客票行程单
ITINERARY/RECEIPT OF E-TICKET
SERIAL NUMBER: 6316372299 4
2015-10-02 12:15
2015-10-08 17:30
电子客票号码 07424977183958
合计 TOTAL CNY 13336.00
填开日期 DATE OF ISSUE 2015-09-28
"""

VAT = """
电子发票 发票号码：26317000002934677164 开票日期 2026年08月19日
税额：13.00 价税合计（小写）¥113.00
增值税专用发票
购买方名称：上海文枢科技有限公司 纳税人识别号
"""

RECEIPT = """
中国工商银行 网上银行电子回单 电子回单号码：0918-3117-0241-1100
金额 ¥100.00元 交易流水号 82900515 记账日期 2026年07月01日
"""


class ParseTextTest(unittest.TestCase):
    def test_didi(self):
        fee, amt, no, tax, kind, buyer, _ = parse_text(DIDI)
        self.assertEqual(no, "26317000003076038180")
        self.assertEqual(fee, "2026-08-25")
        self.assertEqual(amt, "1211.77")
        self.assertEqual(tax, "35.29")
        self.assertEqual(kind, "普票")

    def test_rail(self):
        fee, amt, no, tax, kind, buyer, _ = parse_text(RAIL)
        self.assertEqual(no, "26339190041009328118")
        self.assertEqual(fee, "2026-08-18")
        self.assertNotEqual(fee, "2026-08-20")
        self.assertEqual(amt, "77.00")
        self.assertIsNone(tax)
        self.assertEqual(kind, "普票")

    def test_air(self):
        fee, amt, no, tax, kind, buyer, _ = parse_text(AIR)
        self.assertEqual(no, "63163722994")
        self.assertNotEqual(no, "07424977183958")
        self.assertEqual(fee, "2015-10-02")
        self.assertNotEqual(fee, "2015-10-08")
        self.assertNotEqual(fee, "2015-09-28")
        self.assertEqual(amt, "13336.00")
        self.assertIsNone(tax)
        self.assertEqual(kind, "其他")

    def test_vat_regression(self):
        fee, amt, no, tax, kind, buyer, _ = parse_text(VAT)
        self.assertEqual(no, "26317000002934677164")
        self.assertEqual(fee, "2026-08-19")
        self.assertEqual(amt, "113.00")
        self.assertEqual(tax, "13.00")
        self.assertEqual(kind, "专票")
        self.assertEqual(buyer, "上海文枢科技有限公司")

    def test_receipt_regression(self):
        fee, amt, no, tax, kind, buyer, _ = parse_text(RECEIPT)
        self.assertEqual(no, "82900515")
        self.assertEqual(fee, "2026-07-01")
        self.assertEqual(amt, "100.00")

    def test_e_ticket_no_is_not_invoice_no(self):
        fee, amt, no, tax, kind, buyer, _ = parse_text("电子客票号码 07424977183958")
        self.assertNotEqual(no, "07424977183958")

    def test_paddleocr_noise_didi_tax_and_air_serial(self):
        didi = (
            "电子发统(普通发票) 发票号码：26317000003076038180 旅客运输服务 "
            "开票日期：2026年08月25日 合 计 ¥1176.48 ¥35.29 "
            "价税合计（大写） （小写）¥1211.77"
        )
        fee, amt, no, tax, kind, buyer, _ = parse_text(didi)
        self.assertEqual((no, fee, amt, tax, kind),
                         ("26317000003076038180", "2026-08-25", "1211.77", "35.29", "普票"))
        air = (
            "航空运输电子客票行程单 印刷序号： ITINERARY/RECEIPT OF E-TICKET "
            "6316372299 4 SERIALNUMBER: 2015-10-02 合计TOTAL CNY 13336.00"
        )
        fee, amt, no, tax, kind, buyer, _ = parse_text(air)
        self.assertEqual((no, fee, amt, tax, kind),
                         ("63163722994", "2015-10-02", "13336.00", None, "其他"))


if __name__ == "__main__":
    unittest.main()
