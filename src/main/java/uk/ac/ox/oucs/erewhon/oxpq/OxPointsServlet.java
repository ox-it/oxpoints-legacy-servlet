package uk.ac.ox.oucs.erewhon.oxpq;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import net.sf.gaboto.Gaboto;
import net.sf.gaboto.GabotoConfiguration;
import net.sf.gaboto.GabotoFactory;
import net.sf.gaboto.GabotoSnapshot;
import net.sf.gaboto.time.TimeInstant;

public abstract class OxPointsServlet extends HttpServlet {

  private static final long serialVersionUID = 3396470051960258977L;

  protected static Gaboto gaboto;
  protected static GabotoSnapshot snapshot;
  protected static GabotoConfiguration config;
  protected static Calendar startTime;

  public OxPointsServlet() {
    super();
  }

  public void init() {
    System.err.println("init");
    config = GabotoConfiguration.fromConfigFile();
  
    gaboto = GabotoFactory.readPersistedGaboto(GabotoAccess.getResourceOrDie("graphs.rdf"), GabotoAccess.getResourceOrDie("cdg.rdf"));
    gaboto.recreateTimeDimensionIndex();
  
    startTime = Calendar.getInstance();
  
    snapshot = gaboto.getSnapshot(TimeInstant.from(startTime));
  
  }

  protected void error(HttpServletRequest request, HttpServletResponse response, AnticipatedException exception) {
    response.setContentType("text/html");
    PrintWriter out = null;
    try {
      out = response.getWriter();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    out.println("<html><head><title>OxPoints Anticipated Error</title></head>");
    out.println("<body>");
    out.println("<h2>OxPoints Anticipated Error</h2>");
    out.println("<h3>" + exception.getMessage() + "</h3>");
    out.println("<p>An anticipated error has occured in the application");
    out.println("that runs this website, please contact <a href='mailto:");
    out.println(getSysAdminEmail() + "'>" + getSysAdminName() + "</a>");
    out.println(", with the information given below.</p>");
    out.println("<h3> Invoked with " + request.getRequestURL().toString() + "</h3>");
    if (request.getQueryString() != null)
      out.println("<h3> query " + request.getQueryString() + "</h3>");
    out.println("<h4><font color='red'><pre>");
    exception.printStackTrace(out);
    out.println("</pre></font></h4>");
    out.println("</body></html>");
  
  }

  private String getSysAdminEmail() {
    return "Tim.Pizey@oucs.ox.ac.uk";
  
  }

  private String getSysAdminName() {
    return "Tim Pizey";
  }
  /**
   * Add a Zone URL to buffer.
   *  
   * @param url an empty StringBuffer to append to 
   * @param request the request to interrogate
   */
  public static void appendZoneURL(StringBuffer url, 
                                   HttpServletRequest request) {
    String scheme = request.getScheme();
    url.append(scheme);
    url.append("://");
    url.append(request.getServerName());
    if ((scheme.equals("http") && 
        request.getServerPort() != 80
        )
        ||
        (scheme.equals("https") && 
        request.getServerPort() != 443)) {
      url.append(':');
      url.append(request.getServerPort());
    }
    appendRelativeZoneURL(url,request);
  }

  /**
   * Return the server URL.
   *  
   * @param request the request to interrogate
   */
  public static String getServerURL(HttpServletRequest request) {
    StringBuffer url = new StringBuffer();
    String scheme = request.getScheme();
    url.append(scheme);
    url.append("://");
    url.append(request.getServerName());
    if ((scheme.equals("http") && 
        request.getServerPort() != 80
        )
        ||
        (scheme.equals("https") && 
        request.getServerPort() != 443)) {
      url.append(':');
      url.append(request.getServerPort());
    }
    return url.toString();
  }

  /**
   * Append relative servlet zone url.
   * 
   * Note that this function should return 
   * /zone/servlet from a request of form 
   * http://host/zone/servlet/pathinfo?querystring
   * on all servlet API versions 2.0 through 2.3
   * In 2.0 the zone was returned in the ServletPath 
   * it is now in the ContextPath.
   * @param url StringBuffer to append to 
   * @param request the request to interrogate
   */
  public static void appendRelativeZoneURL (
      StringBuffer url, HttpServletRequest request) {
    url.append(request.getContextPath());
    String servletPath = request.getServletPath();
    if (servletPath != null && !servletPath.equals("")) {
      url.append(servletPath.substring(0, servletPath.lastIndexOf('/')));
      if (servletPath.lastIndexOf('/') == -1) 
        throw new RuntimeException(
            "Servlet Path does not contain a forward slash:" + servletPath);
    }
  }

  /**
   * Retrieve a Zone url.
   * @param request the request to interrogate
   * @return an Url up to the zone specification as a String 
   */
  public static String zoneURL(HttpServletRequest request) {
    StringBuffer url = new StringBuffer();
    appendZoneURL(url, request);
    return url.toString();
  }

  /**
   * Retrieve a Servlet url from a request.
   * @param request the request to interrogate
   * @return an Url up to the servlet specification as a String 
   */
  public static String servletURL(HttpServletRequest request) {
    StringBuffer url = new StringBuffer();
    appendZoneURL(url, request);
    String servlet = request.getServletPath();
    if (servlet != null && !servlet.equals(""))
      url.append(servlet.substring(
                          servlet.lastIndexOf('/'), servlet.length()));
    return url.toString();
  }

  /**
   * Retrieve a relative url from a request.
   * @param request the request to interrogate
   * @return a relative Url  
   */
  public static String getRelativeRequestURL(HttpServletRequest request) {
    StringBuffer url = new StringBuffer();
    url.append(request.getContextPath());
    if (request.getServletPath() != null) url.append(request.getServletPath());
    if (request.getPathInfo() != null) url.append(request.getPathInfo());
    return url.toString();
  }

  /**
   * @param url An url or relative url which may end in a slash
   * @param relativeUrl A relative url which may start with a slash
   * @return an url without a duplicated slash at the join
   */
  public static String concatenateUrls(String url, String relativeUrl) {
    if (url.endsWith("/") && relativeUrl.startsWith("/"))
      return url.substring(0, url.lastIndexOf('/')) + relativeUrl;
    else 
      return url + relativeUrl;
  }


}