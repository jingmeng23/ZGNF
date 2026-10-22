package com.gas.ecommerce.modules.aftersale.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.aftersale.dto.*;
import com.gas.ecommerce.modules.aftersale.entity.Aftersale;
import com.gas.ecommerce.modules.aftersale.vo.AftersaleVO;

public interface AftersaleService extends IService<Aftersale> {
    AftersaleVO create(Long userId, AftersaleCreateRequest request);
    PageResult<AftersaleVO> listMine(Long userId, AftersaleQueryRequest query);
    AftersaleVO detailMine(Long userId, Long id);
    AftersaleVO cancelMine(Long userId, Long id);
    PageResult<AftersaleVO> listAdmin(AftersaleQueryRequest query);
    AftersaleVO detailAdmin(Long id);
    AftersaleVO review(Long id, Long adminId, AftersaleReviewRequest request);
    AftersaleVO complete(Long id, AftersaleCompleteRequest request);
}
