package cn.iocoder.yudao.framework.excel.core.util;

import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.converters.longconverter.LongStringConverter;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.excel.core.handler.ColumnWidthMatchStyleStrategy;
import cn.iocoder.yudao.framework.excel.core.handler.SelectSheetWriteHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Excel 工具类
 *
 * @author 宇擎源码
 */
public class ExcelUtils {

    /**
     * 将列表以 Excel 响应给前端
     *
     * @param response  响应
     * @param filename  文件名
     * @param sheetName Excel sheet 名
     * @param head      Excel head 头
     * @param data      数据列表哦
     * @param <T>       泛型，保证 head 和 data 类型的一致性
     * @throws IOException 写入失败的情况
     */
    public static <T> void write(HttpServletResponse response, String filename, String sheetName,
                                 Class<T> head, List<T> data) throws IOException {
        // 输出 Excel
        FastExcelFactory.write(response.getOutputStream(), head)
                .autoCloseStream(false) // 不要自动关闭，交给 Servlet 自己处理
                .registerWriteHandler(new ColumnWidthMatchStyleStrategy()) // 基于 column 长度，自动适配。最大 255 宽度
                .registerWriteHandler(new SelectSheetWriteHandler(head)) // 基于固定 sheet 实现下拉框
                .registerConverter(new LongStringConverter()) // 避免 Long 类型丢失精度
                .sheet(sheetName).doWrite(data);
        // 设置 header 和 contentType。写在最后的原因是，避免报错时，响应 contentType 已经被修改了
        response.addHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(filename));
        response.setContentType("application/vnd.ms-excel;charset=UTF-8");
    }

    public static <T> List<T> read(MultipartFile file, Class<T> head) throws IOException {
        return read(file, head, 1);
    }

    /**
     * 读取 Excel
     *
     * @param headRowNumber 表头所在行号（从 1 开始）。默认 1 表示首行为表头；
     *                      文枢原模板为「标题行 + 表头行」时传 2。
     */
    public static <T> List<T> read(MultipartFile file, Class<T> head, int headRowNumber) throws IOException {
        // 参考 https://ruoyioffice.com/zM77F 帖子，增加 try 处理，兼容 windows 场景
        try (InputStream inputStream = file.getInputStream()) {
            return read(inputStream, head, headRowNumber);
        }
    }

    /**
     * 从字节数组读取（同一文件可多次尝试不同 headRowNumber）
     */
    public static <T> List<T> read(byte[] bytes, Class<T> head, int headRowNumber) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            return read(inputStream, head, headRowNumber);
        }
    }

    private static <T> List<T> read(InputStream inputStream, Class<T> head, int headRowNumber) {
        return FastExcelFactory.read(inputStream, head, null)
                .autoCloseStream(false)
                .headRowNumber(headRowNumber)
                .doReadAllSync();
    }

}
