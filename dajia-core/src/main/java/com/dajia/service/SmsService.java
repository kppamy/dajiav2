package com.dajia.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.stereotype.Service;

import com.dajia.repository.PropertyRepo;
import com.dajia.util.CommonUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taobao.api.ApiException;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.request.AlibabaAliqinFcSmsNumSendRequest;
import com.taobao.api.response.AlibabaAliqinFcSmsNumSendResponse;

@Service
public class SmsService {
	Logger logger = LoggerFactory.getLogger(SmsService.class);

	@Autowired
	PropertyRepo propertyRepo;

	@Autowired
	EhCacheCacheManager ehcacheManager;

	public String sendSignupMessage(String mobile, boolean allowSend) {
		String returnStatus = CommonUtils.return_val_failed;
		String url = CommonUtils.sms_server_url;
		String appkey = propertyRepo.findByPropertyKey(CommonUtils.sms_app_key).propertyValue;
		String secret = propertyRepo.findByPropertyKey(CommonUtils.sms_app_secret).propertyValue;
		String signupCode = CommonUtils.genRandomNum(4);

		// put signup_code into cache;
		if (null == ehcacheManager.getCacheManager().getCache(CommonUtils.cache_name_signup_code)) {
			ehcacheManager.getCacheManager().addCache(CommonUtils.cache_name_signup_code);
		}
		Cache cache = ehcacheManager.getCacheManager().getCache(CommonUtils.cache_name_signup_code);
		cache.put(new Element(mobile, signupCode));

		TaobaoClient client = new DefaultTaobaoClient(url, appkey, secret);
		AlibabaAliqinFcSmsNumSendRequest req = new AlibabaAliqinFcSmsNumSendRequest();
		req.setRecNum(mobile);
		req.setSmsType("normal");
		req.setSmsFreeSignName("注册验证");
		req.setSmsParam("{\"code\":\"" + signupCode + "\",\"product\":\"打价网\"}");
		req.setSmsTemplateCode(CommonUtils.sms_template_signup);
		try {
			if (allowSend) {
				AlibabaAliqinFcSmsNumSendResponse response = client.execute(req);
				String returnJsonStr = response.getBody();
				if (returnJsonStr.indexOf("\"err_code\":\"0\"") >= 0) {
					returnStatus = CommonUtils.return_val_success;
				}
			} else {
				logger.info("Signup SMS test - Mobile: " + mobile + " / Code: " + signupCode);
				returnStatus = CommonUtils.return_val_success;
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return returnStatus;
	}
}