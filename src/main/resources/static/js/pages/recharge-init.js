/**
 * Recharge page initialization script
 */
(function () {
  'use strict';

  $(document).ready(function () {
    // Check for tab parameter in URL
    const urlParams = new URLSearchParams(window.location.search);
    const tab = urlParams.get('tab');
    if (tab === 'recharge-history') {
      $('.nav-link[href="#lich-su-nap"]').tab('show');
    } else if (tab === 'payment-history') {
      $('.nav-link[href="#lich-su-thanh-toan"]').tab('show');
    }

    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }

    $('#recharge-form').on('submit', function (e) {
      e.preventDefault();
      const amount = parseInt($('#amount').val());
      const paymentMethod = $('input[name="payment_method"]:checked').val();

      if (amount < 10000) {
        showToast('Số tiền nạp tối thiểu là 10,000 VNĐ', false);
        return;
      }

      ApiClient.post('/recharge', {
        amount: amount,
        paymentMethod: paymentMethod
      }, true).done(function (response) {
        if (response.success) {
          showToast('Tạo yêu cầu nạp xu thành công', true);
          $('#recharge-form')[0].reset();
        }
      });
    });

    $('.nav-link').on('click', function () {
      const target = $(this).attr('href');
      if (target === '#lich-su-nap') {
        loadRechargeHistory();
      } else if (target === '#lich-su-thanh-toan') {
        loadPaymentHistory();
      }
    });
  });

  function loadRechargeHistory() {
    ApiClient.get('/recharge/history', null, true).done(function (response) {
      if (response.success && response.data) {
        const history = response.data.content || [];
        const $container = $('#recharge-history');
        $container.empty();

        if (history.length === 0) {
          $container.html('<p>Chưa có lịch sử nạp xu</p>');
          return;
        }

        history.forEach(function (item) {
          const html = `
                            <div class="history-item mb-3">
                                <p><strong>Số tiền:</strong> ${formatNumber(item.amount)} VNĐ</p>
                                <p><strong>Trạng thái:</strong> ${item.status}</p>
                                <p><strong>Ngày:</strong> ${new Date(item.createdAt).toLocaleString('vi-VN')}</p>
                            </div>
                        `;
          $container.append(html);
        });
      }
    });
  }

  function loadPaymentHistory() {
    ApiClient.get('/payments/history', null, true).done(function (response) {
      if (response.success && response.data) {
        const history = response.data.content || [];
        const $container = $('#payment-history');
        $container.empty();

        if (history.length === 0) {
          $container.html('<p>Chưa có lịch sử thanh toán</p>');
          return;
        }

        history.forEach(function (item) {
          const html = `
                            <div class="history-item mb-3">
                                <p><strong>Mô tả:</strong> ${item.description || 'N/A'}</p>
                                <p><strong>Số tiền:</strong> ${formatNumber(item.amount)} VNĐ</p>
                                <p><strong>Ngày:</strong> ${new Date(item.createdAt).toLocaleString('vi-VN')}</p>
                            </div>
                        `;
          $container.append(html);
        });
      }
    });
  }
})();

