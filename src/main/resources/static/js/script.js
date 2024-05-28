$(document).ready(function(){
    $.ajax({
        type: "GET",
        url: "/adminDashboard",
        context: document.body,
        success: function(count){
            $("p.onlineCount").html(count[0]);    //in index.html add a div tag with class="show-part-view"
            $("p.cocCount").html(count[1]);
            $("p.accountPayeeCount").html(count[2]);
            $("p.beftnMainCount").html(count[3])
            $("p.beftnIncentiveCount").html(count[4]);
        }
    });
});

function downloadOnline() {
    window.location="/downloadonline";
    $.ajax({
        type: "GET",
        url: "/countOnlineAfterDownloadButtonClicked",
        context: document.body,
        success: function(count){
            $("p.onlineCount").html(count);
        }
    });
}
function downloadCoc() {
    window.location="/downloadcoc";

}
function downloadAccountPayee() {
    window.location="/downloadaccountpayee";

}
function downloadBeftnMain() {
    window.location="/downloadbeftnMain";

}
function  downloadBeftnIncentive(){
    window.location="/downloadBeftnIncentive";
}