import { CountUp } from "/npm/countup.js/dist/countUp.js";

$(function () {
  let phone_input = $("input[name=phone]");
  let code_input = $("input[name=code]");
  let submit_btn = $(".password_form button");
  let code_form = $(".code_form");
  let password_form = $(".password_form");
  let citizen_status;
  let status_timer;
  let no_errors = $("input")
    .toArray()
    .reduce(function (acc, el) {
      return { ...acc, [$(el).prop("name")]: undefined };
    }, {});

  function send_check_status() {
    phone_input.blur();
    phone_input.spin();
    post("/status", { phone: phone_input.val() })
      .then(function (data) {
        phone_input.unspin();
        citizen_status = data.status;
        send_sms();
      })
      .fail(function () {
        showNotification(
          t("Произошла ошибка, повтор соединения через 3сек"),
          "danger"
        );
        phone_input.unspin();
        status_timer = setTimeout(send_check_status, 3000);
      });
  }

  function send_change_password() {
    submit_btn.btn_spin();
    submit_btn.prop("disabled", true);
    post("/password" + location.search, {
      old_password: $("input[name=old_password]").val(),
      new_password: $("input[name=new_password]").val(),
      repeated_new_password: $("input[name=repeated_new_password]").val(),
    })
      .then(function (data) {
        submit_btn.btn_unspin();
        location.href = data.redirect;
      })
      .fail(function (jqXHR) {
        submit_btn.btn_unspin();
        submit_btn.prop("disabled", false);
        if (jqXHR.status === 400) {
          let data = JSON.parse(jqXHR.responseText);
          toggle_errors({ ...no_errors, ...data.errors });
          if (data.errors && data.errors.phone) reset();
        } else if (jqXHR.status === 401) {
          location.href = data.redirect;
        } else {
          toggle_errors({});
          showNotification(
            t("Ошибка соеденения. Пожалуйста, попробуйте еще раз."),
            "danger"
          );
        }
      });
  }

  let login_timer;

  function phone_login() {
    code_input.spin();
    post("/code-login" + location.search)
      .then(function (data) {
        code_input.unspin();
        location.href = data.redirect;
      })
      .fail(function (jqXHR) {
        if (jqXHR.status === 401) {
          let data = JSON.parse(jqXHR.responseText);
          location.href = data.redirect;
        } else {
          code_input.unspin();
          toggle_errors({ code: undefined });
          showNotification(
            t("Произошла ошибка, повтор соединения через 3сек"),
            "danger"
          );
          login_timer = setTimeout(phone_login, 3000);
        }
      });
  }

  let code_timer;

  function verify_code() {
    code_input.spin();
    post("/verify-code", { phone: phone_input.val(), code: code_input.val() })
      .then(function (data) {
        if (citizen_status === "registered") {
          code_input.unspin();
          code_form.collapse("hide");
          setTimeout(() => password_form.collapse("show"), 350);
        } else {
          phone_login();
        }
      })
      .fail(function (jqXHR) {
        code_input.unspin();
        if (jqXHR.status === 400) {
          let data = JSON.parse(jqXHR.responseText);
          toggle_errors({ ...no_errors, ...data.errors });
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
    phone_input.spin();
    return post("/send-code", { phone: phone_input.val() })
      .then(function (data) {
        code_form.collapse("show");
        phone_input.unspin();
        phone_input.blur();
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
        phone_input.unspin();
        if (jqXHR.status === 400) {
          let data = JSON.parse(jqXHR.responseText);
          toggle_errors(data.errors);
          showNotification(data.error, "danger");
          phone_input.focus();
        } else {
          showNotification(
            t("Произошла ошибка, повтор соединения через 3сек"),
            "danger"
          );
          setTimeout(send_sms, 3000);
        }
      });
  }

  $("#resend").on("click", send_sms);

  let phone_mask;
  if (phone_input[0]) {
    phone_mask = new IMask(phone_input[0], {
      mask: "+998 00 000 00 00",
      definitions: { "9": /9/, "8": /8/ },
    })
      .on("complete", send_check_status)
      .on("accept", function () {
        if (!phone_mask.masked.isComplete) {
          reset();
        }
      });
  }

  let code_mask;
  if (code_input[0]) {
    code_mask = new IMask(code_input[0], { mask: "000000" }).on(
      "complete",
      verify_code
    );
  }

  if ($(".password_form")[0]) {
    submit_btn.click(send_change_password);
  }

  $("[data-countup]").each(function () {
    new CountUp(this, $(this).data("countup"), {
      separator: " ",
      duration: 1 + Math.random(),
      decimalPlaces: $(this).data("decimal-places") || 0,
    }).start();
  });

  function reset() {
    $(".is-invalid").removeClass("is-invalid");
    phone_input.focus();
    $("input[name]:not([name=phone]):not([name=__anti-forgery-token])").val("");
    phone_mask.updateValue();
    code_mask.updateValue();
    $(".collapse").collapse("hide");
    clearInterval(sms_timer);
    clearTimeout(code_timer);
    clearTimeout(status_timer);
    clearTimeout(login_timer);
  }
});
