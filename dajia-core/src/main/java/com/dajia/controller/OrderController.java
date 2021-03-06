package com.dajia.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.dajia.domain.User;
import com.dajia.domain.UserContact;
import com.dajia.domain.UserOrder;
import com.dajia.repository.UserOrderRepo;
import com.dajia.repository.UserRepo;
import com.dajia.service.ApiService;
import com.dajia.service.OrderService;
import com.dajia.service.ProductService;
import com.dajia.service.RefundService;
import com.dajia.service.RewardService;
import com.dajia.service.UserContactService;
import com.dajia.util.CommonUtils;
import com.dajia.util.CommonUtils.OrderStatus;
import com.dajia.vo.OrderVO;
import com.dajia.vo.PaginationVO;
import com.pingplusplus.exception.PingppException;
import com.pingplusplus.model.Charge;
import com.pingplusplus.model.Event;
import com.pingplusplus.model.Refund;
import com.pingplusplus.model.Webhooks;

@RestController
public class OrderController extends BaseController {
	Logger logger = LoggerFactory.getLogger(OrderController.class);

	@Autowired
	private UserRepo userRepo;

	@Autowired
	private UserOrderRepo orderRepo;

	@Autowired
	private ProductService productService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private UserContactService userContactService;

	@Autowired
	private ApiService apiService;

	@Autowired
	private RefundService refundService;

	@Autowired
	private RewardService rewardService;

	@RequestMapping(value = "/user/submitOrder", method = RequestMethod.POST)
	public Charge submitOrder(HttpServletRequest request, HttpServletResponse response, @RequestBody OrderVO orderVO) {
		User user = this.getLoginUser(request, response, userRepo, true);
		UserContact uc = orderVO.userContact;
		if (null != uc) {
			uc = userContactService.updateUserContact(uc, user);
		}

		if (productService.loadProductDetail(orderVO.productId).stock <= 0) {
			return null;
		}

		UserOrder order = new UserOrder();
		order.unitPrice = orderVO.unitPrice;
		order.totalPrice = orderVO.totalPrice;
		order.postFee = orderVO.postFee;
		order.quantity = orderVO.quantity;
		order.payType = orderVO.payType;
		order.productId = orderVO.productId;
		order.productItemId = orderVO.productItemId;
		order.userComments = orderVO.userComments;
		if (null != orderVO.refUserId && orderVO.refUserId.longValue() != user.userId.longValue()) {
			order.refUserId = orderVO.refUserId;
			order.refOrderId = orderVO.refOrderId;
		}
		order.orderDate = new Date();
		order.orderStatus = OrderStatus.PENDING_PAY.getKey();
		order.userId = user.userId;
		order.contactName = uc.contactName;
		order.contactMobile = uc.contactMobile;
		order.address = uc.province.locationValue + " " + uc.city.locationValue + " " + uc.district.locationValue + " "
				+ uc.address1;
		order.trackingId = CommonUtils.genTrackingId(user.userId);
		orderRepo.save(order);

		Charge charge = null;
		try {
			charge = apiService.getPingppCharge(order, user, CommonUtils.getPayTypeStr(order.payType));
			order.pingxxCharge = charge.toString();
			orderRepo.save(order);
		} catch (PingppException e) {
			logger.error(e.getMessage(), e);
		}

		return charge;
	}

	@RequestMapping(value = "/webhooks", method = RequestMethod.POST)
	public void webhooks(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF8");
		// 获取头部所有信息
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = (String) headerNames.nextElement();
			String value = request.getHeader(key);
			logger.info(key + " " + value);
		}
		// 获得 http body 内容
		BufferedReader reader = request.getReader();
		StringBuffer buffer = new StringBuffer();
		String string;
		while ((string = reader.readLine()) != null) {
			buffer.append(string);
		}
		reader.close();
		String eventString = buffer.toString();
		// 解析异步通知数据
		Event event = Webhooks.eventParse(eventString);
		if ("charge.succeeded".equals(event.getType())) {
			Object obj = Webhooks.getObject(eventString);
			if (obj instanceof Charge) {
				logger.info("webhooks 发送了 Charge");
				Charge charge = (Charge) obj;
				String trackingId = charge.getOrderNo();
				logger.info("付款状态：" + charge.getPaid() + " 订单号：" + trackingId);
				UserOrder order = orderRepo.findByTrackingId(trackingId);
				order.paymentId = charge.getId();
				productService.productSold(order);
			}
			response.setStatus(200);
		} else if ("refund.succeeded".equals(event.getType())) {
			Object obj = Webhooks.getObject(eventString);
			if (obj instanceof Refund) {
				logger.info("webhooks 发送了 Refund");
				Refund refund = (Refund) obj;
				String chargeId = refund.getCharge();
				Integer amount = refund.getAmount();
				String desc = refund.getDescription();
				logger.info("退款类型：" + desc + " 退款状态：" + refund.getStatus() + " ChargeId：" + chargeId);
				if (desc.equalsIgnoreCase(CommonUtils.refund_type_refund)) {
					refundService.createRefund(chargeId, new BigDecimal(new Double(amount) / 100),
							CommonUtils.RefundType.REFUND.getKey());
				} else if (desc.equalsIgnoreCase(CommonUtils.refund_type_reward)) {
					refundService.createRefund(chargeId, new BigDecimal(new Double(amount) / 100),
							CommonUtils.RefundType.REWARD.getKey());
				} else {
					refundService.createRefund(chargeId, new BigDecimal(new Double(amount) / 100),
							CommonUtils.RefundType.MANNUAL.getKey());
				}
			}
			response.setStatus(200);
		} else {
			response.setStatus(500);
		}
	}

	@RequestMapping("/user/progresses/{page}")
	public PaginationVO<OrderVO> myProgress(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("page") Integer pageNum) {
		User user = this.getLoginUser(request, response, userRepo, true);
		List<Integer> orderStatusList = new ArrayList<Integer>();
		orderStatusList.add(CommonUtils.OrderStatus.PAIED.getKey());
		orderStatusList.add(CommonUtils.OrderStatus.DELEVERING.getKey());
		orderStatusList.add(CommonUtils.OrderStatus.DELEVRIED.getKey());
		Page<UserOrder> orders = orderService.loadOrdersByUserIdByPage(user.userId, orderStatusList, pageNum);
		List<OrderVO> progressList = new ArrayList<OrderVO>();
		for (UserOrder order : orders) {
			OrderVO ov = orderService.convertOrderVO(order);
			ov.productVO = productService.loadProductDetail(order.productId);
			if (null != ov.productVO && null != ov.productVO.originalPrice && null != ov.productVO.currentPrice) {
				ov.productVO.priceOff = ov.productVO.originalPrice.add(ov.productVO.currentPrice.negate());
			}
			progressList.add(ov);
		}
		PaginationVO<OrderVO> page = CommonUtils.generatePaginationVO(orders, pageNum);
		page.results = progressList;
		return page;
	}

	@RequestMapping("/user/myorders/{page}")
	public PaginationVO<OrderVO> myOrders(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("page") Integer pageNum) {
		User user = this.getLoginUser(request, response, userRepo, true);
		List<Integer> orderStatusList = new ArrayList<Integer>();
		orderStatusList.add(CommonUtils.OrderStatus.PAIED.getKey());
		orderStatusList.add(CommonUtils.OrderStatus.DELEVERING.getKey());
		orderStatusList.add(CommonUtils.OrderStatus.DELEVRIED.getKey());
		orderStatusList.add(CommonUtils.OrderStatus.CLOSED.getKey());
		orderStatusList.add(CommonUtils.OrderStatus.CANCELLED.getKey());
		Page<UserOrder> orders = orderService.loadOrdersByUserIdByPage(user.userId, orderStatusList, pageNum);
		List<OrderVO> orderVoList = new ArrayList<OrderVO>();
		for (UserOrder order : orders) {
			OrderVO ov = orderService.convertOrderVO(order);
			ov.productVO = productService.loadProductDetail(order.productId);
			orderVoList.add(ov);
		}
		PaginationVO<OrderVO> page = CommonUtils.generatePaginationVO(orders, pageNum);
		page.results = orderVoList;
		return page;
	}

	@RequestMapping("/user/order/{trackingId}")
	public OrderVO orderDetail(@PathVariable("trackingId") String trackingId) {
		OrderVO ov = orderService.getOrderDetailByTrackingId(trackingId);
		return ov;
	}

	@RequestMapping("/user/progress/{trackingId}")
	public OrderVO progressDetail(@PathVariable("trackingId") String trackingId) {
		OrderVO ov = orderService.getOrderDetailByTrackingId4Progress(trackingId);
		return ov;
	}
}
