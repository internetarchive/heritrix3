<%@ page import="javax.servlet.*" %>
<%@ page import="javax.servlet.http.*" %>

<%
    // This define is all that differs from the post page.
    final String METHOD = "GET";

    final String COOKIE_NAME = "selftest-login-" + METHOD;
    final String LOGIN = "login";
    final String PASSWORD = "password";

    String method = request.getMethod();
    String login = request.getParameter(LOGIN);
    String password = request.getParameter(PASSWORD);
    Cookie [] cookies = request.getCookies();
    boolean loggedIn = false;
    if (cookies != null) {
        for (int i = 0; i < cookies.length; i++) {
            if (cookies[i].getName().equals(COOKIE_NAME)) {
                loggedIn = true;
                break;
            }
        }
    }

    // If logged in, let them through, else see what parameters are 
    // available.
    if (!loggedIn) {
        if (login == null && password == null ) {
            // Needs to login first.
            response.sendRedirect("index.html");
        } else if (login == null || !login.equals(LOGIN) ||
            password == null || !password.equals(PASSWORD) ||
            method == null || !method.equals(METHOD)) {
            // Add the query string to aid debugging.
            response.sendRedirect("error.html?method=" + method +
                '&' + "login=" + login +
                '&' + "password=" + password);
        } else {
            Cookie cookie = new Cookie("selftest-login-get","successful");
            response.addCookie(cookie);
        }
    }
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
    <head>
        <title>Successful <%=METHOD%> Login Page</title>
        <meta name="author" content="Debian User,,," >
        <meta name="generator" content="screem 0.8.2" >
        <meta name="keywords" content="" >
        <meta http-equiv="content-type" content="text/html; charset=UTF-8" >
        <meta http-equiv="Content-Script-Type" content="text/javascript" >
        <meta http-equiv="Content-Style-Type" content="text/css" >
    </head>
    <body>
            <h1>Successful <%=METHOD%> Login Page</h1>
            <p>You get this page if a successful login.</p>
            <p><a href="get-loggedin.html">Page crawler can get only if it
            successfully negotiated login.</p>
    </body>
</html>
