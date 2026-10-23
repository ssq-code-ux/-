package com.example.englishtutor.controller;
import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.ProfileDTO;
import com.example.englishtutor.entity.StudentProfile;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.service.StudentService;
import com.example.englishtutor.vo.StudentAnalysisVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService service;

    @GetMapping("/students")
    public R<List<User>> list() { return R.ok(service.myStudents()); }

    @GetMapping("/students/search")
    public R<List<User>> search(@RequestParam(required = false) String name) { return R.ok(service.searchStudents(name)); }

    @PutMapping("/students/{id}/bind")
    public R<Void> bind(@PathVariable Long id) { service.bindStudent(id); return R.ok(null); }

    @GetMapping("/students/my-teacher")
    public R<User> myTeacher() { return R.ok(service.myTeacher()); }

    @GetMapping("/teachers")
    public R<List<User>> teachers() { return R.ok(service.listTeachers()); }

    @PostMapping("/students/profile")
    public R<Void> saveProfile(@RequestBody ProfileDTO d) { service.saveProfile(d); return R.ok(null); }

    @GetMapping("/students/{id}/profile")
    public R<StudentProfile> profile(@PathVariable Long id) { return R.ok(service.profile(id)); }

    @GetMapping("/students/{id}/analysis")
    public R<StudentAnalysisVO> analysis(@PathVariable Long id) { return R.ok(service.analysis(id)); }
}
