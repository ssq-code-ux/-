package com.example.englishtutor.service;

import java.util.Map;

/**
 * 家长找家教 —
 * 家长端：填写表单提交需求 → 管理端审核后生成家教单（教师端可见，但看不到家长信息）
 *        → 教师投递后家长可见 → 家长通过某份简历 → 弹窗展示管理端联系方式（电话/微信）。
 */
public interface ParentTutorService {

    /** 家长端：提交找家教需求（表单） */
    void submitDemand(Map<String, Object> body);

    /** 家长端：我的需求单列表（含关联家教单状态 + 已投递教师简历数） */
    Map<String, Object> myDemands(int page, int size);

    /** 家长端：查看某需求单收到的教师简历投递列表 */
    Map<String, Object> myDemandApplications(Long demandId);

    /** 家长端：通过/拒绝某份投递简历（通过后该家教单关闭，弹窗展示管理端联系方式） */
    Map<String, Object> reviewApplication(Long applicationId, boolean approved);

    /** 家长端：申请删除需求单（管理端确认后才会真正删除） */
    void requestDeleteDemand(Long demandId);

    /** 管理端：家长需求列表（keyword 搜订单号/需求编号/家长称呼/电话/微信；status 筛选；
     *  deleteRequested=1 只看删除申请；附关联家教单单号/状态/投递数） */
    Map<String, Object> adminDemands(String keyword, int status, Integer deleteRequested, int page, int size);

    /** 管理端：家长需求详情（含家长联系方式 + 关联家教单信息） */
    Map<String, Object> adminDemandDetail(Long demandId);

    /** 管理端：把家长需求发布为家教单（生成关联 tutor_job，教师端可见但不含家长信息；默认需缴费解锁） */
    Map<String, Object> adminPublishDemand(Long demandId, Map<String, Object> body);

    /** 管理端：确认删除家长需求单（连带删除关联家教单、投递、缴费记录） */
    void adminConfirmDeleteDemand(Long demandId);
}
