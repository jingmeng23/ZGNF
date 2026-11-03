package com.gas.ecommerce.modules.user.dto;
import java.time.LocalDate;
public record ProfileRequest(String nickname,String avatar,String email,String gender,LocalDate birthday) {}
