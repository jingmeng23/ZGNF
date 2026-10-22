package com.gas.ecommerce.modules.system.dto;

import java.util.List;
import jakarta.validation.constraints.*;

/** 全量替换；空列表代表清空，重复或不存在的 ID 会被拒绝。 */
public record AssignmentRequest(@NotNull @Size(max=200) List<@NotNull @Positive Long> ids) { }
