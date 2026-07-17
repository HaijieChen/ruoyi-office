package cn.iocoder.yudao.module.bpm.framework.datasource;

/** Provider for one published dynamic-form data-source type. */
public interface BpmFormDataSourceProvider {

    int TYPE_SQL = 1;
    int TYPE_DICT = 2;
    int TYPE_PLATFORM_API = 3;

    int getType();

    BpmFormDataSourceQueryResult execute(BpmFormDataSourceExecutionContext context);

}
