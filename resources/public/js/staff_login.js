import { CountUp } from "/npm/countup.js/dist/countUp.js";

$(function () {
  let username_input = $("input[name=username]");
  let code_input = $("input[name=code]");
  let password_input = $("input[name=password]");
  let staff_status;
  let submit_btn = $(".submit-button button");
  let status_timer;
  let no_errors = $("input")
    .toArray()
    .reduce(function (acc, el) {
      return { ...acc, [$(el).prop("name")]: undefined };
    }, {});

  function send_username_password() {
    submit_btn.btn_spin();
    post("/staff" + location.search, {
      username: username_input.val(),
      password: password_input.val(),
    })
      .then(function (data) {
        submit_btn.btn_unspin();
        location.href = data.redirect || "/";
      })
      .fail(function (jqXHR) {
        submit_btn.btn_unspin();

        if ([400, 422].includes(jqXHR.status)) {
          let data = JSON.parse(jqXHR.responseText);
          toggle_errors({ ...no_errors, ...data.errors });
          if (data?.errors?.code) {
            submit_btn.hide();
            send_sms();
          }
        } else {
          toggle_errors(no_errors);
          showNotification(
            t("Ошибка соединения. Пожалуйста, попробуйте еще раз."),
            "danger"
          );
        }
      });
  }

  let code_timer;

  function verify_code() {
    code_input.spin();
    code_input.prop("disabled", true);
    post("/staff-verify-code", {
      username: username_input.val(),
      code: code_input.val(),
    })
      .then(send_username_password)
      .fail(function (jqXHR) {
        code_input.unspin();
        if ([400, 422].includes(jqXHR.status)) {
          let data = JSON.parse(jqXHR.responseText);
          toggle_errors({ ...no_errors, ...data.errors });
          code_input.prop("disabled", false);
        } else {
          toggle_errors({ code: undefined });
          showNotification(
            t("Произошла ошибка, повтор соединения через 3сек"),
            "danger"
          );
          clearTimeout(code_timer);
          code_timer = setTimeout(verify_code, 3000);
        }
      });
  }

  let sms_timer;

  function send_sms() {
    username_input.spin();
    return post("/staff-send-code", { username: username_input.val() })
      .then(function (data) {
        $(".code_form").collapse("show");
        username_input.unspin();
        username_input.blur();
        code_input.focus();
        $("#resend")
          .prop("disabled", true)
          .text("Повторно выслать SMS можно через (" + data.ttl + " секунд)");

        if (data.code) {
          code_input
            .closest(".form-group")
            .find(".form-text")
            .text("Code: " + data.code + " (dev mode only)");
        }
        clearInterval(sms_timer);
        sms_timer = setInterval(function () {
          data.ttl -= 1;
          if (data.ttl > 0) {
            $("#resend").text(
              "Повторно выслать SMS можно через (" + data.ttl + " секунд)"
            );
          } else {
            $("#resend").prop("disabled", false).text("Повторно выслать SMS");
            clearInterval(sms_timer);
          }
        }, 1000);
      })
      .fail(function (jqXHR) {
        username_input.unspin();
        if (jqXHR.status === 400) {
          let data = JSON.parse(jqXHR.responseText);
          toggle_errors({ ...no_errors, ...data.errors });
          showNotification(data.error, "danger");
          username_input.focus();
        } else {
          toggle_errors(no_errors);
          showNotification(
            t("Произошла ошибка, повтор соединения через 3сек"),
            "danger"
          );
          setTimeout(send_sms, 3000);
        }
      });
  }

  $("#resend").on("click", send_sms);

  username_input.on("change", reset);
  password_input.on("change", reset);
  let code_mask;
  if (code_input[0]) {
    code_mask = new IMask(code_input[0], { mask: "000000" }).on(
      "complete",
      verify_code
    );
  }

  submit_btn.click(send_username_password);

  $("[data-countup]").each(function () {
    new CountUp(this, $(this).data("countup"), {
      separator: " ",
      duration: 1 + Math.random(),
      decimalPlaces: $(this).data("decimal-places") || 0,
    }).start();
  });

  function reset() {
    submit_btn.show();
    $(".is-invalid").removeClass("is-invalid");
    // username_input.focus();
    // $("input[name]:not([name=username]):not([name=__anti-forgery-token])").val(
    //   ""
    // );
    code_mask.updateValue();
    $(".collapse").collapse("hide");
    clearInterval(sms_timer);
    clearTimeout(code_timer);
    clearTimeout(status_timer);
  }
});
