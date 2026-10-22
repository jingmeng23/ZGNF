package com.gas.ecommerce.modules.auth.dto;

import java.util.List;

public record AdminProfile(Long employeeId, String username, List<String> roles, List<String> permissions) { }
