//今日の日時を表示
window.onload = function () {
    //今日の日時を表示
    var date = new Date()
    var year = date.getFullYear()
    var month = date.getMonth() + 1
    var day = date.getDate()

    var toTwoDigits = function (num, digit) {
      num += ''
      if (num.length < digit) {
        num = '0' + num
      }
      return num
    }

    var yyyy = toTwoDigits(year, 4)
    var mm = toTwoDigits(month, 2)
    var dd = toTwoDigits(day, 2)
    var ymd = yyyy + "-" + mm + "-" + dd;

    document.getElementById("scheduledDate").min = ymd;
}