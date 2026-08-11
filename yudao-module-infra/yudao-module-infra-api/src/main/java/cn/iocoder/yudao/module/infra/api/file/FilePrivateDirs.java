package cn.iocoder.yudao.module.infra.api.file;

/**
 * 私有文件目录约定（HRM 入职资料等）。
 * <p>
 * 通用上传 / 预签名 / 匿名下载必须拒绝；仅 FileAccessApi + 业务鉴权链路可写入/读取。
 */
public final class FilePrivateDirs {

    /** 入职资料私有存储目录前缀 */
    public static final String HRM_ONBOARDING_PRIVATE = "hrm-onboarding-private";

    private FilePrivateDirs() {
    }

    public static boolean isPrivateDirectory(String directoryOrPath) {
        return directoryOrPath != null && directoryOrPath.contains(HRM_ONBOARDING_PRIVATE);
    }

}
