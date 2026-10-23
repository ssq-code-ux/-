package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.entity.LearningResource;
import com.example.englishtutor.entity.Subject;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.LearningResourceMapper;
import com.example.englishtutor.mapper.SubjectMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.ResourceService;
import com.example.englishtutor.util.AliyunOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final LearningResourceMapper mapper;
    private final UserMapper userMapper;
    private final AliyunOSSOperator ossOperator;
    private final SubjectMapper subjectMapper;

    @Override
    public List<String> distinctTypes(Long subjectId) {
        LambdaQueryWrapper<LearningResource> q = new LambdaQueryWrapper<LearningResource>()
                .select(LearningResource::getType)
                .groupBy(LearningResource::getType);
        if (subjectId != null) q.eq(LearningResource::getSubjectId, subjectId);
        List<LearningResource> rows = mapper.selectList(q);
        return rows.stream()
                .map(LearningResource::getType)
                .filter(t -> t != null && !t.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    /** 默认"英语"学科ID（按名称查，不依赖固定ID） */
    private Long defaultEnglishSubjectId() {
        Subject s = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getName, SubjectServiceImpl.DEFAULT_SUBJECT));
        return s != null ? s.getId() : null;
    }

    /** 回填学科名称，供列表/详情展示 */
    private void fillSubjectName(LearningResource r) {
        if (r == null || r.getSubjectId() == null) return;
        Subject s = subjectMapper.selectById(r.getSubjectId());
        if (s != null) r.setSubjectName(s.getName());
    }

    /** 回填发布教师姓名，供列表/详情展示（共享场景区分资料来源） */
    private void fillTeacherName(LearningResource r) {
        if (r == null || r.getTeacherId() == null) return;
        User t = userMapper.selectById(r.getTeacherId());
        if (t != null) r.setTeacherName(t.getRealName() != null ? t.getRealName() : t.getUsername());
    }

    /** OSS 上传并设置 fileUrl（带重试，由 AliyunOSSOperator 内部实现） */
    private void upload(MultipartFile file, LearningResource r) {
        String originalName = file.getOriginalFilename();
        long sizeMB = file.getSize() / (1024 * 1024);
        log.info("学习资料上传: {} ({}MB)", originalName, sizeMB);
        try {
            String url = ossOperator.upload(file.getBytes(), originalName, file.getContentType());
            r.setFileUrl(url);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            log.error("资料上传OSS失败: {} — {}", originalName, msg);
            if (msg.contains("timeout") || msg.contains("Timeout") || msg.contains("timed out")) {
                throw new BizException("上传超时，文件过大或网络不稳定，请稍后重试");
            }
            if (msg.contains("connect") || msg.contains("Connect") || msg.contains("UnknownHost")) {
                throw new BizException("无法连接到文件存储服务，请检查网络后重试");
            }
            throw new BizException("文件上传失败: " + msg);
        }
    }

    /**
     * 获取当前登录用户应看到的学习资料所属老师ID。
     * — 老师：只能看到自己发布的资料
     * — 学生：只能看到其绑定老师发布的资料
     */
    private Long resolveTeacherId() {
        Long userId = AuthContext.userId();
        String role = AuthContext.role();
        if ("teacher".equals(role)) {
            return userId;
        }
        // 学生：查其绑定的老师
        User student = userMapper.selectById(userId);
        if (student == null || student.getTeacherId() == null) {
            throw new BizException("您尚未绑定老师，无法查看学习资料");
        }
        return student.getTeacherId();
    }

    /** 校验资源是否属于当前老师（仅老师可执行修改/删除操作） */
    private void checkOwnership(LearningResource r) {
        if (!AuthContext.userId().equals(r.getTeacherId())) {
            throw new BizException("只能操作自己发布的资料");
        }
    }

    public void add(LearningResource r, MultipartFile file) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可管理资料");
        r.setTeacherId(AuthContext.userId());
        r.setCreateTime(LocalDateTime.now());
        // 未指定学科时默认归入"英语"
        if (r.getSubjectId() == null) r.setSubjectId(defaultEnglishSubjectId());
        if (file != null && !file.isEmpty()) upload(file, r);
        mapper.insert(r);
    }

    public Page<LearningResource> list(String type, String keyword, Long subjectId, Long viewTeacherId, int page, int size) {
        Long targetTeacherId;
        if (viewTeacherId != null && viewTeacherId > 0) {
            // 教师间共享：仅教师角色可查看其他教师发布的资料
            if (!"teacher".equals(AuthContext.role())) throw new BizException("仅教师可查看其他教师的共享资料");
            User target = userMapper.selectById(viewTeacherId);
            if (target == null || !"teacher".equals(target.getRole())) throw new BizException("目标教师不存在");
            targetTeacherId = viewTeacherId;
        } else {
            // 不指定教师：老师=自己的资料，学生=绑定老师的资料（原有行为）
            targetTeacherId = resolveTeacherId();
        }
        LambdaQueryWrapper<LearningResource> q = new LambdaQueryWrapper<>();
        q.eq(LearningResource::getTeacherId, targetTeacherId);
        if (type != null && !type.isEmpty()) q.eq(LearningResource::getType, type);
        // 不传 subjectId = 查全部（向后兼容）；传了才按学科过滤
        if (subjectId != null && subjectId > 0) q.eq(LearningResource::getSubjectId, subjectId);
        if (keyword != null && !keyword.isEmpty())
            q.like(LearningResource::getTitle, keyword);
        q.orderByDesc(LearningResource::getCreateTime);
        Page<LearningResource> p = mapper.selectPage(new Page<>(page, size), q);
        p.getRecords().forEach(this::fillSubjectName);
        p.getRecords().forEach(this::fillTeacherName);
        return p;
    }

    public void update(Long id, String title, String type, String content, MultipartFile file, String fileUrl, Long subjectId) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可编辑资料");
        LearningResource r = mapper.selectById(id);
        if (r == null) throw new BizException("资料不存在");
        checkOwnership(r);
        r.setTitle(title);
        r.setType(type);
        r.setContent(content);
        if (subjectId != null && subjectId > 0) r.setSubjectId(subjectId);
        if (fileUrl != null && !fileUrl.isEmpty()) r.setFileUrl(fileUrl);
        if (file != null && !file.isEmpty()) upload(file, r);
        mapper.updateById(r);
    }

    public LearningResource getById(Long id) {
        LearningResource r = mapper.selectById(id);
        if (r == null) return null;
        // 校验访问权限：教师角色可查看所有教师共享的资料；学生只能看绑定老师的
        if (!"teacher".equals(AuthContext.role())) {
            Long allowedTeacherId = resolveTeacherId();
            if (!allowedTeacherId.equals(r.getTeacherId())) {
                throw new BizException("无权查看该资料");
            }
        }
        fillSubjectName(r);
        fillTeacherName(r);
        return r;
    }

    public void delete(Long id) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可删除资料");
        LearningResource r = mapper.selectById(id);
        if (r == null) throw new BizException("资料不存在");
        checkOwnership(r);
        mapper.deleteById(id);
    }

    /** 当前教师名下是否已有同标题的资料（用于批量复制判重） */
    private boolean alreadyCopied(LearningResource src) {
        return mapper.selectCount(new LambdaQueryWrapper<LearningResource>()
                .eq(LearningResource::getTeacherId, AuthContext.userId())
                .eq(LearningResource::getTitle, src.getTitle())) > 0;
    }

    /** 一键复制指定教师的全部资料到自己的资料库（按标题判重，重复点击不会产生重复记录） */
    @Transactional
    public int copyAllFromTeacher(Long teacherId) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可复制资料");
        if (AuthContext.userId().equals(teacherId)) throw new BizException("这是您自己的资料库，无需复制");
        User target = userMapper.selectById(teacherId);
        if (target == null || !"teacher".equals(target.getRole())) throw new BizException("目标教师不存在");
        List<LearningResource> list = mapper.selectList(new LambdaQueryWrapper<LearningResource>()
                .eq(LearningResource::getTeacherId, teacherId));
        int count = 0;
        for (LearningResource src : list) {
            if (alreadyCopied(src)) continue; // 已复制过则跳过
            LearningResource c = new LearningResource();
            c.setTitle(src.getTitle());
            c.setType(src.getType());
            c.setSubjectId(src.getSubjectId());
            c.setLevel(src.getLevel());
            c.setContent(src.getContent());
            c.setTags(src.getTags());
            c.setFileUrl(src.getFileUrl());
            c.setTeacherId(AuthContext.userId());
            c.setCreateTime(LocalDateTime.now());
            mapper.insert(c);
            count++;
        }
        return count;
    }

    /** 复制其他教师的资料到当前教师自己的资料库（含图片/文件 URL，OSS 链接直接复用） */
    public LearningResource copy(Long id) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可复制资料");
        LearningResource src = mapper.selectById(id);
        if (src == null) throw new BizException("资料不存在");
        if (AuthContext.userId().equals(src.getTeacherId())) throw new BizException("这是您自己发布的资料，无需复制");
        LearningResource c = new LearningResource();
        c.setTitle(src.getTitle());
        c.setType(src.getType());
        c.setSubjectId(src.getSubjectId());
        c.setLevel(src.getLevel());
        c.setContent(src.getContent());
        c.setTags(src.getTags());
        c.setFileUrl(src.getFileUrl());
        c.setTeacherId(AuthContext.userId());
        c.setCreateTime(LocalDateTime.now());
        mapper.insert(c);
        return c;
    }

    /** 按账号/姓名模糊搜索教师（排除自己），供教师间共享资料使用 */
    public List<User> searchTeachers(String keyword) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "teacher")
                .ne(User::getId, AuthContext.userId());
        if (keyword != null && !keyword.isEmpty()) {
            q.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword));
        }
        q.orderByAsc(User::getUsername).last("LIMIT 50");
        List<User> list = userMapper.selectList(q);
        list.forEach(u -> u.setPassword(null)); // 脱敏
        return list;
    }

    /**
     * 文件代理：后端从 OSS 拉取附件后流式转发给浏览器。
     * 浏览器只和自己的后端通信（同源），彻底规避 OSS 跨域（CORS）限制。
     * 权限校验复用 getById（教师可看任意共享资料，学生只能看绑定老师的）。
     */
    public void streamFile(Long id, int index, boolean download, HttpServletResponse response) {
        LearningResource r = getById(id);
        if (r == null) throw new BizException("资料不存在");
        List<String> urls = r.getFileUrl() == null ? List.of()
                : Arrays.stream(r.getFileUrl().split(",")).filter(s -> !s.isBlank()).toList();
        if (index < 0 || index >= urls.size()) throw new BizException("文件不存在");
        String url = urls.get(index);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(120)).build();
            HttpResponse<InputStream> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new BizException("文件获取失败（HTTP " + resp.statusCode() + "），文件可能已被删除");
            }
            String contentType = resp.headers().firstValue("Content-Type").orElse("application/octet-stream");
            response.setContentType(contentType);
            resp.headers().firstValueAsLong("Content-Length").ifPresent(response::setContentLengthLong);
            if (download) {
                // 下载文件名：资料标题 + 原扩展名（OSS 对象名是 UUID，对用户无意义）
                String ext = url.contains(".") ? url.substring(url.lastIndexOf('.')) : "";
                String base = (r.getTitle() != null ? r.getTitle() : "file").replaceAll("[\\\\/:*?\"<>|]", "_");
                String filename = URLEncoder.encode(base + ext, StandardCharsets.UTF_8).replace("+", "%20");
                response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
            }
            try (InputStream in = resp.body(); OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件代理失败: {}", url, e);
            throw new BizException("文件获取失败，请稍后重试");
        }
    }

    /** 代理拉取用的 HTTP 客户端（单例复用连接池） */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
}
