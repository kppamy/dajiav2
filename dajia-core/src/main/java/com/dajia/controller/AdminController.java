package com.dajia.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.dajia.domain.Product;
import com.dajia.domain.ProductItem;
import com.dajia.domain.User;
import com.dajia.domain.UserOrder;
import com.dajia.repository.ProductItemRepo;
import com.dajia.repository.ProductRepo;
import com.dajia.repository.UserOrderRepo;
import com.dajia.service.OrderService;
import com.dajia.service.ProductService;
import com.dajia.service.RefundService;
import com.dajia.service.UserService;
import com.dajia.util.CommonUtils;
import com.dajia.util.CommonUtils.OrderStatus;
import com.dajia.vo.OrderVO;
import com.dajia.vo.PaginationVO;
import com.dajia.vo.ProductVO;

@RestController
public class AdminController extends BaseController {
	Logger logger = LoggerFactory.getLogger(AdminController.class);

	@Autowired
	private ProductRepo productRepo;

	@Autowired
	private ProductItemRepo productItemRepo;

	@Autowired
	private UserOrderRepo orderRepo;

	@Autowired
	private RefundService refundService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private ProductService productService;

	@Autowired
	private UserService userService;

	@RequestMapping("/admin/robotorder/{pid}")
	public @ResponseBody UserOrder robotOrder(@PathVariable("pid") Long pid) {
		UserOrder order = orderService.generateRobotOrder(pid, 1);
		return order;
	}

	@RequestMapping("/admin/sync")
	public @ResponseBody Map<String, String> syncAllProducts() {
		productService.syncProductsAll();
		Map<String, String> map = new HashMap<String, String>();
		map.put("result", "success");
		return map;
	}

	@RequestMapping("/admin/products/{page}")
	public PaginationVO<ProductItem> productsByPage(@PathVariable("page") Integer pageNum) {
		Page<ProductItem> products = productService.loadProductsByPage(pageNum);
		PaginationVO<ProductItem> page = CommonUtils.generatePaginationVO(products, pageNum);
		return page;
	}

	@RequestMapping("/admin/product/{pid}")
	public ProductVO product(@PathVariable("pid") Long pid) {
		ProductVO product = productService.loadProductDetail(pid);
		if (null == product) {
			product = new ProductVO();
			product.productId = 0L;
			product.productItemId = 0L;
		}
		return product;
	}

	@RequestMapping("/admin/product/remove/{pid}")
	public Product removeProduct(@PathVariable("pid") Long pid) {
		Product product = productRepo.findOne(pid);
		if (null != product) {
			product.isActive = CommonUtils.ActiveStatus.NO.toString();
			if (null != product.productItems)
				for (ProductItem pi : product.productItems) {
					pi.isActive = CommonUtils.ActiveStatus.NO.toString();
				}
			productRepo.save(product);
		}
		return product;
	}

	@RequestMapping(value = "/admin/product/{pid}", method = RequestMethod.POST)
	public @ResponseBody ProductVO modifyProduct(@PathVariable("pid") Long pid, @RequestBody ProductVO productVO) {
		if (pid == productVO.productId) {
			Product product = null;
			if (pid.longValue() != 0L) {
				product = productRepo.findOne(pid);
				if (null != product.productItems && product.productItems.size() > 0) {
					for (ProductItem pi : product.productItems) {
						if (pi.isActive.equalsIgnoreCase(CommonUtils.ActiveStatus.YES.toString())) {
							CommonUtils.updateProductItemWithReq(pi, productVO);
							break;
						}
					}
					CommonUtils.updateProductWithReq(product, productVO);
					productRepo.save(product);
					return productService.convertProductVO(product);
				}
			} else {
				product = new Product();
			}

			CommonUtils.updateProductWithReq(product, productVO);
			// productRepo.save(product);

			ProductItem productItem = new ProductItem();
			CommonUtils.updateProductItemWithReq(productItem, productVO);
			productItem.product = product;
			product.productItems = new ArrayList<ProductItem>();
			product.productItems.add(productItem);
			productRepo.save(product);
			return productService.convertProductVO(product);
		} else {
			return null;
		}
	}

	@RequestMapping("/admin/product/{pid}/republish")
	public ProductVO productRepublish(@PathVariable("pid") Long pid) {
		ProductVO pv = productService.loadProductDetail(pid);
		if (null == pv) {
			pv = new ProductVO();
			pv.productId = 0L;
			pv.productItemId = 0L;
		}
		if (pv.productStatus == CommonUtils.ProductStatus.EXPIRED.getKey()) {
			pv.productItemId = 0L;
			Product product = productRepo.findOne(pid);
			for (ProductItem pi : product.productItems) {
				if (pi.isActive.equalsIgnoreCase(CommonUtils.ActiveStatus.YES.toString())) {
					pi.isActive = CommonUtils.ActiveStatus.NO.toString();
				}
			}
			ProductItem pi = new ProductItem();
			pi.product = product;
			pi.productStatus = CommonUtils.ProductStatus.INVALID.getKey();
			pi.isActive = CommonUtils.ActiveStatus.YES.toString();
			product.productItems.add(pi);
			productRepo.save(product);
		}
		return pv;
	}

	@RequestMapping("/admin/users/{page}")
	public PaginationVO<User> usersByPage(@PathVariable("page") Integer pageNum) {
		Page<User> users = userService.loadUsersByPage(pageNum);
		PaginationVO<User> page = CommonUtils.generatePaginationVO(users, pageNum);
		return page;
	}

	@RequestMapping("/admin/orders/{page}")
	public PaginationVO<OrderVO> ordersByPage(@PathVariable("page") Integer pageNum, HttpServletRequest request) {
		String filterVal = request.getParameter("filter");
		Page<UserOrder> orders = orderService.loadOrdersByPage(pageNum, filterVal);
		List<OrderVO> orderVoList = new ArrayList<OrderVO>();
		for (UserOrder order : orders) {
			OrderVO ov = orderService.convertOrderVO(order);
			orderService.fillOrderVO(ov, order);
			orderVoList.add(ov);
		}
		PaginationVO<OrderVO> page = CommonUtils.generatePaginationVO(orders, pageNum);
		page.results = orderVoList;
		return page;
	}

	@RequestMapping("/admin/order/{orderId}")
	public OrderVO order(@PathVariable("orderId") Long orderId) {
		UserOrder order = orderRepo.findOne(orderId);
		if (null == order) {
			return null;
		}
		OrderVO ov = orderService.convertOrderVO(order);
		orderService.fillOrderVO(ov, order);
		ov.refundList = refundService.getRefundListByOrderId(orderId);
		return ov;
	}

	@RequestMapping("/admin/order/{orderId}/deliver")
	public UserOrder deliverOrder(@PathVariable("orderId") Long orderId, HttpServletRequest request) {
		String logisticTrackingId = request.getParameter("lti");
		String logisticAgent = request.getParameter("la");
		UserOrder order = orderRepo.findOne(orderId);
		order.orderStatus = OrderStatus.DELEVERING.getKey();
		order.logisticAgent = logisticAgent;
		order.logisticTrackingId = logisticTrackingId;
		orderRepo.save(order);
		return order;
	}

	@RequestMapping("/admin/order/{orderId}/comments")
	public UserOrder addComments(@PathVariable("orderId") Long orderId, HttpServletRequest request) {
		String comments = request.getParameter("comments");
		String adminComments = request.getParameter("adminComments");
		UserOrder order = orderRepo.findOne(orderId);
		order.comments = comments;
		order.adminComments = adminComments;
		orderRepo.save(order);
		return order;
	}

	@RequestMapping("/admin/order/{orderId}/finish")
	public UserOrder finishOrder(@PathVariable("orderId") Long orderId) {
		UserOrder order = orderRepo.findOne(orderId);
		order.orderStatus = OrderStatus.DELEVRIED.getKey();
		orderRepo.save(order);
		return order;
	}

	@RequestMapping("/admin/order/{orderId}/close")
	public UserOrder closeOrder(@PathVariable("orderId") Long orderId) {
		UserOrder order = orderRepo.findOne(orderId);
		order.orderStatus = OrderStatus.CLOSED.getKey();
		orderRepo.save(order);
		return order;
	}
}
