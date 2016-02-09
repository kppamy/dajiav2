angular.module('starter.controllers', [ "ui.bootstrap", "countTo" ]).controller('ProdCtrl', function($scope, $http) {
	console.log('产品列表...');
	var loadProducts = function() {
		return $http.get('/products/').success(function(data, status, headers, config) {
			$scope.products = data;
			$scope.$broadcast('scroll.refreshComplete');
		}).error(function(data, status, headers, config) {
			console.log('request failed...');
		});
	}
	loadProducts();
	$scope.doRefresh = function() {
		loadProducts();
	};
})

.controller(
		'ProdDetailCtrl',
		function($scope, $rootScope, $stateParams, $http, $cookies, $window, $timeout, $ionicSlideBoxDelegate,
				$ionicModal) {
			console.log('产品详情...')
			modalInit($scope, $ionicModal, 'login');
			$http.get('/product/' + $stateParams.pid).success(
					function(data, status, headers, config) {
						// console.log(data);
						var product = data;
						$scope.product = product;
						$ionicSlideBoxDelegate.update();
						$scope.orderNeeded = product.maxOrder - product.orderNum;
						$scope.nextPriceOff = product.priceOff / product.maxOrder;
						var amt = (product.originalPrice - product.currentPrice)
								/ (product.originalPrice - product.targetPrice) * 100;
						$scope.countTo = product.currentPrice;
						$scope.countFrom = product.originalPrice;
						$scope.buyNow = function() {
							var loginUser = $cookies.get('dajia_user');
							if (loginUser == null) {
								$rootScope.$broadcast('event:auth-loginRequired');
							} else {
								$window.location.href = '#/tab/prod/' + $stateParams.pid + '/order';
							}
						}
						$timeout(function() {
							$scope.progressValue = amt;
						}, 1000);
					}).error(function(data, status, headers, config) {
				console.log('request failed...');
			});
			$scope.progressValue = 0;
		})

.controller('ProgCtrl', function($scope, $ionicModal, $timeout, Mocks) {
	console.log('进度列表...');
	modalInit($scope, $ionicModal, 'login');
	var orders = Mocks.getMyOrders();
	orders.forEach(function(o) {
		o.product = Mocks.getProduct(o.pid);
		o.progressValue = o.product.priceOff / (o.product.oriPrice - o.product.targetPrice) * 100;
	});
	$scope.myOrders = orders;
})

.controller('ProgDetailCtrl', function($scope, $stateParams, Mocks) {
	console.log('进度详情...')
	var order = Mocks.getOrder($stateParams.orderId);
	order.product = Mocks.getProduct(order.pid);
	order.contactInfo = Mocks.getContact(order.contactId);
	order.progressValue = order.product.priceOff / (order.product.oriPrice - order.product.targetPrice) * 100;
	$scope.order = order;
})

.controller('MineCtrl', function($scope) {
	console.log('我的打价...');
	$scope.settings = {
		enableFriends : true
	};
})

.controller('OrderCtrl', function($scope, $rootScope, $stateParams, $http, $ionicModal, $timeout, $ionicLoading) {
	console.log('订单页面...')
	modalInit($scope, $ionicModal, 'login');
	$scope.order = {
		'quantity' : 1,
		'unitPrice' : 0,
		'totalPrice' : 0
	};
	var quota = 5;
	$http.get('/product/' + $stateParams.pid).success(function(data, status, headers, config) {
		var product = data;
		$scope.orderItem = product;
		$scope.totalPrice = product.price;
		quota = product.buyQuota;
		$scope.order.productId = product.productId;
		$scope.order.unitPrice = product.currentPrice;
		$scope.order.totalPrice = $scope.order.quantity * $scope.order.unitPrice;
	}).error(function(data, status, headers, config) {
		console.log('request failed...');
	});
	$http.get('/user/loginuserinfo').success(function(data, status, headers, config) {
		var loginuser = data;
		$scope.loginuser = loginuser;
	}).error(function(data, status, headers, config) {
		console.log('request failed...');
	});
	$http.get('/locations').success(function(data, status, headers, config) {
		$scope.provinces = data;
		$scope.userContact = $scope.loginuser.userContact;
		$scope.provinces.forEach(function(p) {
			if (p.locationKey == $scope.loginuser.userContact.province.locationKey) {
				$scope.userContact.province = p;
				p.children.forEach(function(c) {
					if (c.locationKey == $scope.loginuser.userContact.city.locationKey) {
						$scope.userContact.city = c;
						c.children.forEach(function(d) {
							if (d.locationKey == $scope.loginuser.userContact.district.locationKey) {
								$scope.userContact.district = d;
								return;
							}
						});
						return;
					}
				});
				return;
			}
		});
	}).error(function(data, status, headers, config) {
		console.log('request failed...');
	});
	$scope.submit = function() {
		if ($scope.userContact.contactId == null) {
			console.log('new userContact.');
		}
		var name = $scope.userContact.contactName;
		var mobile = $scope.userContact.contactMobile;
		var province = $scope.userContact.province;
		var city = $scope.userContact.city;
		var district = $scope.userContact.district;
		var address = $scope.userContact.address1;

		if (!name || !mobile || !province || !city || !district || !address) {
			popWarning('请输入完整信息', $timeout, $ionicLoading);
			return;
		}
		var mobileReg = /^(((13[0-9]{1})|159|153)+\d{8})$/;
		if (mobile.length != 11 || !mobileReg.test(mobile)) {
			popWarning('请数据正确的手机号码', $timeout, $ionicLoading);
			return;
		}

		$scope.order.userContact = $scope.userContact;

		$http.post('/user/submitOrder', $scope.order).success(function(data, status, headers, config) {
			var orderId = data;
			popWarning('订单编号：' + orderId, $timeout, $ionicLoading);
		}).error(function(data, status, headers, config) {
			console.log('request failed...');
		});
	}
	$scope.add = function() {
		if ($scope.order.quantity >= quota) {
			popWarning('该产品每个账号限购' + quota + '件', $timeout, $ionicLoading);
			return;
		}
		$scope.order.quantity += 1;
		$scope.order.totalPrice = $scope.order.quantity * $scope.order.unitPrice;
	}
	$scope.remove = function() {
		if ($scope.order.quantity > 1) {
			$scope.order.quantity -= 1;
			$scope.order.totalPrice = $scope.order.quantity * $scope.order.unitPrice;
		}
	}
})

.controller('SignInCtrl', function($scope, $ionicLoading, $timeout, AuthService, $ionicModal) {
	modalInit($scope, $ionicModal, 'signup');
	$scope.login = {
		'mobile' : null,
		'password' : null
	};

	$scope.submit = function() {
		if (!$scope.login.mobile || !$scope.login.password) {
			popWarning('请输入完整信息', $timeout, $ionicLoading);
			return;
		}
		AuthService.login($scope.login);
	};

	$scope.signup = function() {
		$scope.openModal('signup');
	}

	$scope.$on('event:auth-loginRequired', function(e, rejection) {
		$scope.openModal('login');
	});

	$scope.$on('event:auth-loginConfirmed', function() {
		$scope.closeModal('login');
		popWarning('登陆成功', $timeout, $ionicLoading);
	});

	$scope.$on('event:auth-login-failed', function(e, status) {
		var error = "登录失败";
		if (status == 401) {
			error = "用户名或密码错误";
		}
		popWarning(error, $timeout, $ionicLoading);
	});

	$scope.$on('event:auth-logout-complete', function() {
		// $state.go("home");
		$scope.openModal();
	});
})

.controller('SignUpCtrl', function($scope, $http, $ionicLoading, $timeout, AuthService) {
	$scope.signup = {
		'mobile' : null,
		'password' : null
	};

	$scope.submit = function() {
		var mobile = $scope.signup.mobile;
		var password = $scope.signup.password;
		if (!mobile || !password) {
			popWarning('请输入完整信息', $timeout, $ionicLoading);
			return;
		}
		var mobileReg = /^(((13[0-9]{1})|159|153)+\d{8})$/;
		if (mobile.length != 11 || !mobileReg.test(mobile)) {
			popWarning('请数据正确的手机号码', $timeout, $ionicLoading);
			return;
		}
		if (password.length < 6) {
			popWarning('请输入至少六位数的密码', $timeout, $ionicLoading);
			return;
		}
		AuthService.signup($scope.signup);
	};

	$scope.$on('event:auth-signup-failed', function(e, status) {
		var error = "注册失败";
		popWarning(error, $timeout, $ionicLoading);
	});

	$scope.$on('event:auth-signup-success', function() {
		$scope.closeModal('signup');
		popWarning('注册成功', $timeout, $ionicLoading);
	});
})

.controller('SignOutCtrl', function($scope, AuthService) {
	AuthService.logout();
});

var modalInit = function($scope, $ionicModal, modalType) {
	// console.log($ionicModal);
	$ionicModal.fromTemplateUrl('templates/' + modalType + '.html', {
		scope : $scope,
		animation : 'slide-in-up'
	}).then(function(modal) {
		$scope['modal_' + modalType] = modal;
	});
	$scope.openModal = function(type) {
		$scope['modal_' + type].show();
	};
	$scope.closeModal = function(type) {
		$scope['modal_' + type].hide();
	};
	$scope.$on('$destroy', function() {
		$scope['modal_' + modalType].remove();
	});
}

var popWarning = function(msg, $timeout, $ionicLoading) {
	$ionicLoading.show({
		template : msg
	});
	$timeout(function() {
		$ionicLoading.hide();
	}, 800);
}