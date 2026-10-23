package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.FeignAuthContext;
import com.example.englishtutor.entity.AdminJobConfig;
import com.example.englishtutor.entity.TeacherProfile;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.AdminJobConfigMapper;
import com.example.englishtutor.mapper.TeacherProfileMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.InviteCodeService;
import com.example.englishtutor.service.TutorProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师简历 + 接单配置实现。
 * AdminJobConfig 是单行表（id=1），SQL 迁移里已插入占位行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorProfileServiceImpl implements TutorProfileService {

    private static final int CONFIG_ID = 1;

    private final TeacherProfileMapper profileMapper;
    private final AdminJobConfigMapper configMapper;
    private final UserMapper userMapper;
    private final InviteCodeService inviteCodeService;

    // ==================== 教师端 ====================

    @Override
    public Map<String, Object> myProfile() {
        assertTeacher();
        TeacherProfile p = profileMapper.selectById(FeignAuthContext.userId());
        Map<String, Object> m = new LinkedHashMap<>();
        if (p != null) {
            m.put("subjects", p.getSubjects());
            m.put("availableTime", p.getAvailableTime());
            m.put("longTerm", p.getLongTerm());
            m.put("name", p.getName());
            m.put("gender", p.getGender());
            m.put("age", p.getAge());
            m.put("school", p.getSchool());
            m.put("major", p.getMajor());
            m.put("gradeYear", p.getGradeYear());
            m.put("teachYears", p.getTeachYears());
            m.put("hometown", p.getHometown());
            m.put("textbookVer", p.getTextbookVer());
            m.put("teachScope", p.getTeachScope());
            m.put("hasMaterial", p.getHasMaterial());
            m.put("otherAdvantage", p.getOtherAdvantage());
            m.put("teachAdvantage", p.getTeachAdvantage());
            m.put("photoUrl", p.getPhotoUrl());
            m.put("awardImgs", splitImgs(p.getAwardImgs()));
            m.put("updateTime", p.getUpdateTime());
        }
        return m;
    }

    @Override
    public void saveProfile(Map<String, Object> body) {
        assertTeacher();
        if (body == null) throw new BizException("请求体不能为空");

        Long teacherId = FeignAuthContext.userId();
        TeacherProfile p = profileMapper.selectById(teacherId);
        boolean isNew = (p == null);
        if (isNew) {
            p = new TeacherProfile();
            p.setTeacherId(teacherId);
        }
        if (body.containsKey("subjects")) p.setSubjects(str(body.get("subjects")));
        if (body.containsKey("availableTime")) p.setAvailableTime(str(body.get("availableTime")));
        if (body.containsKey("longTerm")) p.setLongTerm(str(body.get("longTerm")));
        if (body.containsKey("name")) p.setName(str(body.get("name")));
        if (body.containsKey("gender")) p.setGender(str(body.get("gender")));
        if (body.containsKey("age")) p.setAge(intOrNull(body.get("age")));
        if (body.containsKey("school")) p.setSchool(str(body.get("school")));
        if (body.containsKey("major")) p.setMajor(str(body.get("major")));
        if (body.containsKey("gradeYear")) p.setGradeYear(str(body.get("gradeYear")));
        if (body.containsKey("teachYears")) p.setTeachYears(str(body.get("teachYears")));
        if (body.containsKey("hometown")) p.setHometown(str(body.get("hometown")));
        if (body.containsKey("textbookVer")) p.setTextbookVer(str(body.get("textbookVer")));
        if (body.containsKey("teachScope")) p.setTeachScope(str(body.get("teachScope")));
        if (body.containsKey("hasMaterial")) p.setHasMaterial(str(body.get("hasMaterial")));
        if (body.containsKey("otherAdvantage")) p.setOtherAdvantage(str(body.get("otherAdvantage")));
        if (body.containsKey("teachAdvantage")) p.setTeachAdvantage(str(body.get("teachAdvantage")));
        if (body.containsKey("photoUrl")) p.setPhotoUrl(str(body.get("photoUrl")));
        if (body.containsKey("awardImgs")) p.setAwardImgs(joinImgs(body.get("awardImgs")));
        p.setUpdateTime(LocalDateTime.now());

        if (isNew) {
            profileMapper.insert(p);
        } else {
            profileMapper.updateById(p);
        }
    }

    @Override
    public Map<String, Object> notice() {
        // 须知/收款码是给教师看的公开信息，但要求登录（网关已拦），这里限定教师角色
        assertTeacher();
        AdminJobConfig c = configMapper.selectById(CONFIG_ID);
        Map<String, Object> m = new LinkedHashMap<>();
        if (c != null) {
            m.put("noticeText", c.getNoticeText());
            m.put("noticeImgs", splitImgs(c.getNoticeImgs()));
            m.put("phone", c.getPhone());
            m.put("wechat", c.getWechat());
            m.put("payQrUrl", c.getPayQrUrl());
        }
        return m;
    }

    /**
     * 我的邀请信息（需求 5）：教师把自己的邀请码发给同行，对方注册时填写即可。
     * 这里把“码 + 已邀请人数 + 名单”一次给全，教师端不用发三个请求。
     */
    @Override
    public Map<String, Object> myInviteInfo() {
        assertTeacher();
        Long teacherId = FeignAuthContext.userId();
        User u = userMapper.selectById(teacherId);
        if (u == null) {
            throw new BizException("教师账号不存在");
        }
        String code = inviteCodeService.ensure(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inviteCode", code);
        m.put("invitedCount", inviteCodeService.invitedCount(teacherId));
        m.put("invitedUsers", inviteCodeService.invitedUsers(teacherId));
        return m;
    }

    // ==================== 管理端 ====================

    @Override
    public Map<String, Object> adminGetConfig() {
        assertAdmin();
        AdminJobConfig c = configMapper.selectById(CONFIG_ID);
        Map<String, Object> m = new LinkedHashMap<>();
        if (c != null) {
            m.put("noticeText", c.getNoticeText());
            m.put("noticeImgs", splitImgs(c.getNoticeImgs()));
            m.put("phone", c.getPhone());
            m.put("wechat", c.getWechat());
            m.put("payQrUrl", c.getPayQrUrl());
            m.put("updateTime", c.getUpdateTime());
        }
        return m;
    }

    @Override
    public void adminSaveConfig(Map<String, Object> body) {
        assertAdmin();
        if (body == null) throw new BizException("请求体不能为空");

        AdminJobConfig c = configMapper.selectById(CONFIG_ID);
        if (c == null) {
            c = new AdminJobConfig();
            c.setId(CONFIG_ID);
        }
        if (body.containsKey("noticeText")) c.setNoticeText(str(body.get("noticeText")));
        if (body.containsKey("noticeImgs")) c.setNoticeImgs(joinImgs(body.get("noticeImgs")));
        if (body.containsKey("phone")) c.setPhone(str(body.get("phone")));
        if (body.containsKey("wechat")) c.setWechat(str(body.get("wechat")));
        if (body.containsKey("payQrUrl")) c.setPayQrUrl(str(body.get("payQrUrl")));
        c.setUpdaterId(FeignAuthContext.userId());
        c.setUpdateTime(LocalDateTime.now());

        if (configMapper.selectById(CONFIG_ID) == null) {
            configMapper.insert(c);
        } else {
            configMapper.updateById(c);
        }
    }

    // ==================== 内部工具 ====================

    private void assertTeacher() {
        if (!"teacher".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要教师账号");
        }
    }

    private void assertAdmin() {
        if (!"admin".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要管理员账号");
        }
    }

    /** 逗号分隔的图片串 → List（空安全） */
    static List<String> splitImgs(String joined) {
        if (joined == null || joined.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(joined.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    /** 前端传数组或逗号串 → 存库的逗号串 */
    static String joinImgs(Object v) {
        if (v == null) return null;
        if (v instanceof List<?> list) {
            String s = list.stream().map(String::valueOf).map(String::trim)
                    .filter(x -> !x.isEmpty()).reduce((a, b) -> a + "," + b).orElse(null);
            return s == null || s.isBlank() ? null : s;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer intOrNull(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new BizException("年龄必须是数字");
        }
    }
}
