package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import cn.idev.excel.util.DateUtils;
import cn.idev.excel.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 将 Excel「交易日期」单元格统一转为字符串，供档 1 解析器处理。
 * <ul>
 *   <li>数值日期（Excel serial）→ {@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>文本 → 原样 trim</li>
 *   <li>永不抛转换异常，避免整单 500</li>
 * </ul>
 */
public class FinanceReceiptImportDateStringConverter implements Converter<String> {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Class<?> supportJavaTypeKey() {
        return String.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        // 字段级 converter 时按单元格实际类型分支；此处声明 STRING 即可
        return CellDataTypeEnum.STRING;
    }

    @Override
    public String convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                    GlobalConfiguration globalConfiguration) {
        if (cellData == null || cellData.getType() == null || cellData.getType() == CellDataTypeEnum.EMPTY) {
            return null;
        }
        if (cellData.getType() == CellDataTypeEnum.NUMBER) {
            BigDecimal number = cellData.getNumberValue();
            if (number == null) {
                return null;
            }
            boolean use1904 = Boolean.TRUE.equals(globalConfiguration.getUse1904windowing());
            LocalDateTime dateTime = DateUtils.getLocalDateTime(number.doubleValue(), use1904);
            return dateTime == null ? null : DATE_TIME.format(dateTime);
        }
        if (cellData.getType() == CellDataTypeEnum.DATE) {
            // 部分实现会以 DATE 类型给出
            LocalDateTime dateTime = cellData.getData() instanceof LocalDateTime
                    ? (LocalDateTime) cellData.getData()
                    : null;
            if (dateTime != null) {
                return DATE_TIME.format(dateTime);
            }
        }
        String stringValue = cellData.getStringValue();
        if (StringUtils.isEmpty(stringValue)) {
            return null;
        }
        return stringValue.trim();
    }

    @Override
    public WriteCellData<?> convertToExcelData(String value, ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        return new WriteCellData<>(value == null ? "" : value);
    }

}
