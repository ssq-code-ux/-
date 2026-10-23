package com.example.englishtutor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信推送组件 — 把站内通知同步推到用户微信
 *
 * 为什么需要它：家长/学生不会主动打开网页看铃铛，通知必须去用户已经在看的地方。
 * 走第三方转发服务（免费、无需微信服务号资质），支持两种主流通道，按令牌前缀自动识别：
 *   PushPlus   — 令牌为 32 位十六进制，POST https://www.pushplus.plus/send
 *   Server酱   — 令牌以 SCT/sctp 开头（Turbo 版 / Server酱³）
 *
 * 令牌是每个用户自己的（去对应网站微信扫码登录即可获取），存在 sys_user.push_token，
 * 因此不需要任何服务端密钥配置，开箱可用；push.wechat.enabled=false 可全局关闭。
 *
 * 降级策略：令牌为空、开关关闭、网络异常一律只记日志返回 false，绝不影响主业务流程。
 */
@Slf4j
@Component
public class WechatPushOperator {

    /** 全局开关：关闭后所有推送直接跳过（不影响站内通知） */
    @Value("${push.wechat.enabled:${WECHAT_PUSH_ENABLED:true}}")
    private boolean enabled;

    @Value("${push.wechat.pushplus-url:https://www.pushplus.plus/send}")
    private String pushplusUrl;

    /** 内容最大长度 — 超长的通知正文截断，避免第三方接口拒收 */
    private static final int MAX_CONTENT = 500;

    private final ObjectMapper json = new ObjectMapper();
    private volatile RestClient restClient;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 推送一条消息到用户微信（旁路调用用这个：失败只记日志，不影响主流程）
     *
     * @param token   用户的推送令牌（sys_user.push_token），为空表示该用户未开启推送
     * @param title   消息标题
     * @param content 消息正文
     * @return 是否推送成功（未开启/未配置/失败均返回 false）
     */
    public boolean send(String token, String title, String content) {
        return trySend(token, title, content) == null;
    }

    /**
     * 推送并带回失败原因 — 给「发送测试」用
     *
     * @return 成功返回 null；失败返回可直接展示给用户的原因
     */
    public String trySend(String token, String title, String content) {
        if (!enabled) {
            log.debug("[微信推送] 全局开关已关闭，跳过：{}", title);
            return "管理员已停用微信推送";
        }
        if (!notBlank(token)) {
            log.debug("[微信推送] 用户未配置推送令牌，跳过：{}", title);
            return "尚未配置推送令牌";
        }
        String safeTitle = notBlank(title) ? title : "英语家教系统通知";
        String safeContent = notBlank(content) ? content : safeTitle;
        if (safeContent.length() > MAX_CONTENT) {
            safeContent = safeContent.substring(0, MAX_CONTENT) + "…";
        }
        try {
            return isServerChan(token)
                    ? sendByServerChan(token.trim(), safeTitle, safeContent)
                    : sendByPushPlus(token.trim(), safeTitle, safeContent);
        } catch (Exception e) {
            // 第三方服务不可用不能影响批改/布置作业等主流程，只记日志
            log.warn("[微信推送] 发送异常（已忽略）：{} — {}", safeTitle, e.getMessage());
            return "连不上推送服务：" + e.getMessage();
        }
    }

    /** PushPlus：{"token":"...","title":"...","content":"...","template":"txt"} → {"code":200}；成功返回 null */
    private String sendByPushPlus(String token, String title, String content) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("title", title);
        body.put("content", content);
        body.put("template", "txt");

        String resp = client().post()
                .uri(pushplusUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return checkResult("PushPlus", resp, 200, "code", "msg");
    }

    /**
     * Server酱：POST https://sctapi.ftqq.com/{SendKey}.send → {"code":0}
     * Server酱³（sctp 前缀，形如 sctp123t8xxxx）网关不同：https://{123}.push.ft07.com/send
     */
    private String sendByServerChan(String token, String title, String content) throws Exception {
        // sctp 令牌形如 sctp{编号}t{密钥}，取中间编号拼专属网关；拿不到编号就回退到 Turbo 网关
        int tIdx = token.indexOf('t', 4);
        String url = (token.startsWith("sctp") && tIdx > 4)
                ? "https://" + token.substring(4, tIdx) + ".push.ft07.com/send"
                : "https://sctapi.ftqq.com/" + token + ".send";

        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("desp", content);

        String resp = client().post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return checkResult("Server酱", resp, 0, "code", "message");
    }

    /**
     * 统一解析响应码：两家的成功码不同（PushPlus 200 / Server酱 0）
     *
     * @return 成功返回 null；失败返回给人看的原因
     */
    private String checkResult(String channel, String resp, int okCode,
                                String codeField, String msgField) throws Exception {
        if (resp == null) {
            log.warn("[微信推送] {} 返回空响应", channel);
            return channel + " 返回空响应，请稍后重试";
        }
        JsonNode node = json.readTree(resp);
        int code = node.path(codeField).asInt(-1);
        if (code == okCode) {
            log.info("[微信推送] {} 发送成功", channel);
            return null;
        }
        String msg = node.path(msgField).asText("");
        log.warn("[微信推送] {} 发送失败 code={} msg={}", channel, code, msg);
        return friendlyReason(channel, code, msg);
    }

    /**
     * 把第三方返回码翻译成人话。实测最容易踩的坑：
     * PushPlus 官网网页上发送走登录态不校验 token，所以「官网测试成功、API 报 903」
     * 基本就是 token 复制错了或已被重置，得回官网重新复制。
     */
    private static String friendlyReason(String channel, int code, String msg) {
        if ("PushPlus".equals(channel)) {
            if (code == 903) {
                return "令牌无效（903）：PushPlus 不认识这枚 token。注意官网网页发送不校验 token，"
                        + "请重新扫码登录 pushplus.plus，复制首页展示的最新 token";
            }
            if (code == 905) {
                return "PushPlus 账户未实名认证（905），请到官网完成实名后再发送";
            }
        }
        return channel + " 返回错误码 " + code + (msg.isBlank() ? "" : "：" + msg);
    }

    /** Server酱 令牌以 SCT（Turbo）或 sctp（Server酱³）开头，其余按 PushPlus 处理 */
    private static boolean isServerChan(String token) {
        String t = token.trim();
        return t.startsWith("SCT") || t.startsWith("sctp");
    }

    /**
     * 懒加载单例客户端。超时设得很短（连接 5s / 读取 8s）：
     * 推送是旁路增强，宁可失败也不能拖慢教师提交批改的响应。
     */
    private RestClient client() {
        if (restClient == null) {
            synchronized (this) {
                if (restClient == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(5000);
                    factory.setReadTimeout(8000);
                    restClient = RestClient.builder().requestFactory(factory).build();
                }
            }
        }
        return restClient;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
