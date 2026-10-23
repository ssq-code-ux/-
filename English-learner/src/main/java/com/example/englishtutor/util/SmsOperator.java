package com.example.englishtutor.util;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阿里云短信发送组件
 *
 * 配置项（application.yml 的 sms.aliyun 节点，可用环境变量覆盖）：
 *   access-key-id / access-key-secret  — 阿里云 AccessKey
 *   sign-name                          — 已审核通过的短信签名
 *   template-code                      — 已审核通过的模板 CODE
 *
 * 降级策略：任一配置缺失时不发送，仅打印日志并返回 false，
 * 保证未开通短信服务的开发/演示环境功能不受影响。
 */
@Slf4j
@Component
public class SmsOperator {

    @Value("${sms.aliyun.access-key-id:${ALIYUN_SMS_ACCESS_KEY_ID:}}")
    private String accessKeyId;

    @Value("${sms.aliyun.access-key-secret:${ALIYUN_SMS_ACCESS_KEY_SECRET:}}")
    private String accessKeySecret;

    @Value("${sms.aliyun.sign-name:${ALIYUN_SMS_SIGN_NAME:}}")
    private String signName;

    @Value("${sms.aliyun.template-code:${ALIYUN_SMS_TEMPLATE_CODE:}}")
    private String templateCode;

    private volatile Client client;
    private final ObjectMapper json = new ObjectMapper();

    /** 是否已完成短信配置（签名 + 模板 + AK 齐备） */
    public boolean isEnabled() {
        return notBlank(accessKeyId) && notBlank(accessKeySecret)
                && notBlank(signName) && notBlank(templateCode);
    }

    /**
     * 发送模板短信
     *
     * @param phone  接收手机号
     * @param params 模板变量，如 {"name":"小明","title":"Unit5词汇"}
     * @return 是否发送成功（未配置或失败返回 false）
     */
    public boolean send(String phone, Map<String, String> params) {
        if (!notBlank(phone)) {
            log.warn("[SMS] 手机号为空，跳过发送");
            return false;
        }
        if (!isEnabled()) {
            log.warn("[SMS] 阿里云短信未配置（需 sms.aliyun.* 四项），跳过发送 → {} {}", phone, params);
            return false;
        }
        try {
            SendSmsRequest req = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam(json.writeValueAsString(params));
            SendSmsResponse resp = client().sendSms(req);
            String code = resp.getBody() != null ? resp.getBody().getCode() : null;
            if ("OK".equals(code)) {
                log.info("[SMS] 发送成功 → {} 参数 {}", phone, params);
                return true;
            }
            log.error("[SMS] 发送失败 → {} code={} message={}",
                    phone, code, resp.getBody() != null ? resp.getBody().getMessage() : "");
            return false;
        } catch (Exception e) {
            log.error("[SMS] 发送异常 → {}", phone, e);
            return false;
        }
    }

    private Client client() throws Exception {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    Config config = new Config()
                            .setAccessKeyId(accessKeyId)
                            .setAccessKeySecret(accessKeySecret);
                    config.endpoint = "dysmsapi.aliyuncs.com";
                    client = new Client(config);
                }
            }
        }
        return client;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
