$(function () {
  $("select[name=role]").on("change", function () {
    if ($(this).val() === "inspector") {
      $("#staff-region, #staff-active").slideDown();
    } else {
      $("#staff-region, #staff-active").slideUp();
    }
  });
  $("input[name*=phone]").each(function (_, el) {
    new IMask(el, {
      mask: "+998 00 000 00 00",
      definitions: { "9": /9/, "8": /8/ },
    });
  });
});
$.fn.checkbox_confirm = function () {
  let checkbox = this;
  let confirm_box = $(`
          <div class="row justify-content-center">
            <div class="p-2 checkbox_confirm___msg"></div>
            <div class="d-flex px-5">
              <button class="btn btn-primary checkbox_confirm__btn_ok btn-sm">${t(
                "Ок"
              )}</button>
              <button class="btn btn-danger ml-2 checkbox_confirm__btn_cancel btn-sm">${t(
                "Отмена"
              )}</button>
            </div>
          </div>`);
  checkbox.tooltip({
    html: true,
    trigger: "manual",
    title: confirm_box[0],
  });
  $(confirm_box)
    .find(".checkbox_confirm__btn_ok")
    .on("click", function (_) {
      checkbox.prop("checked", !checkbox.prop("checked"));
      checkbox.tooltip("hide");
      checkbox.trigger("change");
    });
  $(confirm_box)
    .find(".checkbox_confirm__btn_cancel")
    .on("click", function (_) {
      checkbox.tooltip("hide");
    });
  checkbox.on("click", function (e) {
    e.preventDefault();
    e.stopPropagation();
    checkbox.tooltip("show");
    $(".checkbox_confirm___msg").html(
      !checkbox.prop("checked")
        ? checkbox.data("uncheck-message")
        : checkbox.data("check-message")
    );
  });
};

$("[data-toggle=checkbox_confirm]").checkbox_confirm();
$("[name=has_password]").on("change", function (e) {
  $("[name=new_password], .generate-password-btn").prop(
    "disabled",
    !$(this).prop("checked")
  );
  $("[name=new_password]").val(
    $(this).prop("checked") ? Math.random().toString(36).slice(-9) : ""
  );
});
$(".generate-password-btn").on("click", function (e) {
  $("[name=new_password]").val(Math.random().toString(36).slice(-7));
});
