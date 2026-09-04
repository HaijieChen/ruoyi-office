package cn.iocoder.yudao.module.bpm.framework.im;

/**
 * 外呼 HTTP，便于单测替换。
 */
public interface ImHttpPoster {

    String get(String url);

    String postJson(String url, String jsonBody);

    String postJson(String url, String jsonBody, String authorization);
}
