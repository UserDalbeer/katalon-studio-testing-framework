package com.kms.katalon.core.windows.driver;

import java.io.IOException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.http.HttpClient.Factory;
import org.openqa.selenium.remote.internal.OkHttpClient;
import org.openqa.selenium.support.ui.FluentWait;

import com.kms.katalon.core.configuration.RunConfiguration;
import com.kms.katalon.core.logging.KeywordLogger;
import com.kms.katalon.core.network.ProxyInformation;
import com.kms.katalon.core.util.internal.JsonUtil;
import com.kms.katalon.core.util.internal.ProxyUtil;
import com.kms.katalon.core.windows.driver.RemoteHttpClientFactory;
import com.kms.katalon.core.windows.constants.CoreWindowsMessageConstants;
import com.kms.katalon.core.windows.constants.WindowsDriverConstants;
import com.kms.katalon.core.windows.keyword.helper.WindowsActionSettings;
import com.thoughtworks.selenium.SeleniumException;

import io.appium.java_client.MobileCommand;
import io.appium.java_client.remote.AppiumCommandExecutor;
import io.appium.java_client.windows.WindowsDriver;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class WindowsDriverFactory {

    private static final int DEFAULT_CONNECT_TIMEOUT_IN_SECONDS = 120;

    private static final int DEFAULT_READ_TIMEOUT_IN_SECONDS = 10800;

    private static final int DEFAULT_WRITE_TIMEOUT_IN_SECONDS = 0;

    private static KeywordLogger logger = KeywordLogger.getInstance(WindowsDriverFactory.class);

    public static final String DESIRED_CAPABILITIES_PROPERTY = "desiredCapabilities";

    public static final String WIN_APP_DRIVER_PROPERTY = "winAppDriverUrl";

    private static WindowsSession windowsSession;

    public static WindowsDriver<WebElement> getWindowsDriver() {
        return windowsSession.getRunningDriver();
    }

    public static WindowsSession getWindowsSession() {
        return windowsSession;
    }

    @SuppressWarnings("unchecked")
    public static WindowsDriver<WebElement> startApplication(String appFile, String appTitle)
            throws SeleniumException, IOException, URISyntaxException {
        Map<String, Object> userConfigProperties = RunConfiguration.getDriverPreferencesProperties("Windows");
        if (userConfigProperties == null) {
            userConfigProperties = new HashMap<String, Object>();
        }

        String remoteAddressURLAsString = (String) userConfigProperties.getOrDefault(WIN_APP_DRIVER_PROPERTY,
                WindowsDriverConstants.DEFAULT_WIN_APP_DRIVER_URL);
        URL remoteAddressURL = new URL(remoteAddressURLAsString);
        if (!remoteAddressURLAsString.equals(WindowsDriverConstants.DEFAULT_WIN_APP_DRIVER_URL)) {
            logger.logInfo(String.format("Starting application %s on the test machine at address %s", appFile,
                    remoteAddressURL.toString()));
            logger.logRunData(WIN_APP_DRIVER_PROPERTY, remoteAddressURL.toString());
        } else {
            logger.logInfo(String.format("Starting application %s on the local machine at address %s", appFile,
                    remoteAddressURL.toString()));
        }

        Object desiredCapabilitiesAsObject = userConfigProperties.getOrDefault(DESIRED_CAPABILITIES_PROPERTY, null);
        DesiredCapabilities desiredCapabilities = (desiredCapabilitiesAsObject instanceof Map)
                ? new DesiredCapabilities((Map<String, Object>) desiredCapabilitiesAsObject)
                : new DesiredCapabilities();
        logger.logRunData(DESIRED_CAPABILITIES_PROPERTY, JsonUtil.toJson(desiredCapabilities.toJson(), false));

        ProxyInformation proxyInfo = RunConfiguration.getProxyInformation();
        WindowsDriver<WebElement> windowsDriver = startApplication(remoteAddressURL, appFile, desiredCapabilities, proxyInfo, appTitle).getRunningDriver();
        
        windowsDriver.manage().timeouts().implicitlyWait(RunConfiguration.getTimeOut(), TimeUnit.SECONDS);
        
        return windowsDriver;

    }

    public static WindowsSession startApplication(URL remoteAddressURL, String appFile,
            DesiredCapabilities initCapabilities, ProxyInformation proxyInfo, String appTitle)
            throws SeleniumException, IOException, URISyntaxException {
        try {
            windowsSession = new WindowsSession(remoteAddressURL, appFile, initCapabilities, proxyInfo);

            DesiredCapabilities desiredCapabilities = new DesiredCapabilities(initCapabilities);
            desiredCapabilities.setCapability("app", appFile);
            WindowsDriver<WebElement> windowsDriver = newWindowsDriver(remoteAddressURL, desiredCapabilities, proxyInfo);
            windowsDriver.getWindowHandle();

            windowsSession.setApplicationDriver(windowsDriver);
            return windowsSession;
        } catch (WebDriverException e) {
            if (StringUtils.isEmpty(appTitle)) {
                throw e;
            }
            if (!(e instanceof NoSuchWindowException) && !(e instanceof SessionNotCreatedException)) {
                throw e;
            }
            if (e.getMessage() != null && e.getMessage().contains("The system cannot find the file specified")) {
                // appFile is not correct
                throw e;
            }
            if ("Root".equals(appFile)) {
                throw e;
            }
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            desiredCapabilities.setCapability("app", "Root");
            WindowsDriver<WebElement> desktopDriver = newWindowsDriver(remoteAddressURL, desiredCapabilities, proxyInfo);

            FluentWait<WindowsDriver<WebElement>> wait = new FluentWait<WindowsDriver<WebElement>>(desktopDriver)
                    .withTimeout(Duration.ofMillis(WindowsActionSettings.DF_WAIT_ACTION_TIMEOUT_IN_MILLIS))
                    .pollingEvery(Duration.ofMillis(5000))
                    .ignoring(NoSuchElementException.class);

            logger.logInfo(MessageFormat.format(CoreWindowsMessageConstants.WindowsActionHelper_INFO_START_FINDING_WINDOW, appFile,
                    WindowsActionSettings.DF_WAIT_ACTION_TIMEOUT_IN_MILLIS));

            WebElement webElement = wait.until(new Function<WindowsDriver<WebElement>, WebElement>() {
                @Override
                public WebElement apply(WindowsDriver<WebElement> driver) {
                    WebElement webElement = null;
                    for (WebElement element : desktopDriver.findElementsByTagName("Window")) {
                        try {
                            if (element.getText().contains(appTitle)) {
                                webElement = element;
                                break;
                            }
                        } catch (WebDriverException ignored) {}
                    }

                    if (webElement == null) {
                        for (WebElement element : desktopDriver.findElementsByTagName("Pane")) {
                            try {
                                if (element.getText().contains(appTitle)) {
                                    webElement = element;
                                    break;
                                }
                            } catch (WebDriverException ignored) {}
                        }
                    }
                    return webElement;
                }
            });

            if (webElement == null) {
                throw e;
            }

            String appTopLevelWindow = webElement.getAttribute("NativeWindowHandle");

            if (StringUtils.isNotEmpty(appTopLevelWindow)) {
                DesiredCapabilities retryDesiredCapabilities = new DesiredCapabilities(initCapabilities);
                retryDesiredCapabilities.setCapability("appTopLevelWindow",
                        Integer.toHexString(Integer.parseInt(appTopLevelWindow)));
                WindowsDriver<WebElement> windowsDriver = newWindowsDriver(remoteAddressURL, retryDesiredCapabilities,
                        proxyInfo);

                windowsSession.setApplicationDriver(windowsDriver);
                windowsSession.setDesktopDriver(desktopDriver);
                return windowsSession;
            }
            throw e;
        }
    }

    public static WindowsDriver<WebElement> newWindowsDriver(URL remoteAddressURL,
            DesiredCapabilities desiredCapabilities, ProxyInformation proxyInfo) throws IOException, URISyntaxException {
        if (remoteAddressURL != null) {
            return new WindowsDriver<WebElement>(getAppiumExecutorForRemoteDriver(remoteAddressURL, proxyInfo),
                    desiredCapabilities);
        } else {
            return new WindowsDriver<WebElement>(desiredCapabilities);
        }
    }

    public static AppiumCommandExecutor getAppiumExecutorForRemoteDriver(URL remoteWebServerUrl, ProxyInformation proxyInfo)
            throws IOException, URISyntaxException {
        Factory clientFactory = getClientFactoryForRemoteDriverExecutor(proxyInfo, remoteWebServerUrl);
        AppiumCommandExecutor executor = new AppiumCommandExecutor(MobileCommand.commandRepository, remoteWebServerUrl,
                clientFactory);
        return executor;
    }

    private static Factory getClientFactoryForRemoteDriverExecutor(ProxyInformation proxyInfo, URL url)
            throws URISyntaxException, IOException {
        okhttp3.OkHttpClient.Builder client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

        if (StringUtils.isNotBlank(url.getUserInfo())) {
            String[] userInfo = url.getUserInfo().split(":");
            if (userInfo != null && userInfo.length == 2) {
                Authenticator basicAuthenticator = new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(userInfo[0], userInfo[1]);
                        return response.request().newBuilder().header("Authorization", credential).build();
                    }
                };
                client = client.authenticator(basicAuthenticator);
            }
        }

        Proxy proxy = proxyInfo != null ? ProxyUtil.getProxy(proxyInfo) : null;;
        if (proxy != null) {
            String proxyUser = proxyInfo.getUsername();
            String proxyPassword = proxyInfo.getPassword();

            Authenticator proxyAuthenticator = new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    String credential = Credentials.basic(proxyUser, proxyPassword);
                    return response.request().newBuilder().header("Proxy-Authorization", credential).build();
                }
            };

            client = client.proxy(proxy).proxyAuthenticator(proxyAuthenticator);
        }

        return new RemoteHttpClientFactory(new OkHttpClient(client.build(), url));
    }
}
