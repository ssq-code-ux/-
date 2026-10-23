package com.example.englishtutor.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.entity.LearningResource;
import com.example.englishtutor.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface ResourceService {
    void add(LearningResource r, MultipartFile file);
    void update(Long id, String title, String type, String content, MultipartFile file, String fileUrl, Long subjectId);
    /** viewTeacherId 为空=看当前用户范围（老师=自己/学生=绑定老师）；教师角色可传其他教师ID查看其共享资料 */
    Page<LearningResource> list(String type, String keyword, Long subjectId, Long viewTeacherId, int page, int size);
    LearningResource getById(Long id);
    void delete(Long id);
    /** 已存在的资料类型（去重，供新增资料时下拉建议）；subjectId 非空则只看该学科下的类型 */
    List<String> distinctTypes(Long subjectId);
    /** 按账号/姓名搜索教师（排除自己），用于教师间共享资料 */
    List<User> searchTeachers(String keyword);
    /** 复制其他教师的资料到当前教师自己的资料库，返回新记录 */
    LearningResource copy(Long id);
    /** 一键复制指定教师的全部资料到自己的资料库（按标题判重跳过已复制的），返回实际复制数量 */
    int copyAllFromTeacher(Long teacherId);
    /** 文件代理：从 OSS 拉取附件流式转发给浏览器（预览/下载），规避 OSS 跨域 */
    void streamFile(Long id, int index, boolean download, HttpServletResponse response);
}
