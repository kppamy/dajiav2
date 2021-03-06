package com.dajia.vo;

import java.util.Date;
import java.util.List;

import com.dajia.domain.UserContact;

public class LoginUserVO {
	public Long userId;

	public String userName;

	public String password;

	public String email;

	public String mobile;

	public String oauthType;

	public String oauthUserId;

	public String headImgUrl;

	public String signupCode;

	public String signinCode;

	public String bindingCode;

	public Date loginDate;

	public String loginIP;

	public String isAdmin;

	public UserContact userContact;

	public List<UserContact> userContacts;
}
