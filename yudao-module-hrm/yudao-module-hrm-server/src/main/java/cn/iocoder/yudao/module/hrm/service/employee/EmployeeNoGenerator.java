package cn.iocoder.yudao.module.hrm.service.employee;

/**
 * 根据现有工号数字最大值生成下一个工号，并保持原位宽。
 */
public final class EmployeeNoGenerator {

    private EmployeeNoGenerator() {
    }

    /**
     * @param maxNumericEmployeeNo 当前租户纯数字工号中数值最大的那条（原样字符串）；没有则 null
     * @return 下一位工号。无现有工号时从 0001 起
     */
    public static String next(String maxNumericEmployeeNo) {
        if (maxNumericEmployeeNo == null || maxNumericEmployeeNo.isBlank()) {
            return "0001";
        }
        long value = Long.parseLong(maxNumericEmployeeNo.trim()) + 1;
        int width = Math.max(maxNumericEmployeeNo.trim().length(), String.valueOf(value).length());
        return String.format("%0" + width + "d", value);
    }

}
