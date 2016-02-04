package com.dajia.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dajia.domain.User;
import com.dajia.repository.UserRepo;
import com.dajia.util.UserUtils;
import com.dajia.vo.LoginUserVO;

public class BaseController {
	Logger logger = LoggerFactory.getLogger(BaseController.class);

	protected User getLoginUser(HttpServletRequest request, HttpServletResponse response, UserRepo userRepo) {
		User user = null;
		HttpSession session = request.getSession(true);
		LoginUserVO loginUser = (LoginUserVO) session.getAttribute(UserUtils.session_user);
		if (null != loginUser) {
			user = userRepo.findByMobile(loginUser.mobile);
		}
		if (null == user) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return null;
		}
		return user;
	}
}
