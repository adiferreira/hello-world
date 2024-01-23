hello-world
===========

Just a test

To be done later!

=== RECEITAS ===

12/01 -> 18/01 (84)

19/01 -> 25/01 (84)

26/01 -> 01/02 (72-50=22)

02/02 -> 08/2 (72-3=69)

09/02 -> 15/02 (72)

16/02 -> 22/02 (72-3=69)

23/02 -> 29/02 (72-50=22)

01/03 -> 07/03 (72)

08/03 -> 14/03 (72)

15/03 -> 21/03 (72)

22/03 -> 28/03 (72-20=22)

29/03 -> 04/04 (72-3=69)

05/04 -> 11/04 (72)

12/04 -> 18/04 (72)

19/04 -> 25/04 (72-50=22)

26/04 -> 02/05 (72-3=69)

03/05 -> 09/05 (72)

10/05 -> 16/05 (72)

17/05 -> 23/05 (72-50=22)

24/05 -> 30/05 (72-3=69)

31/05 -> 06/06 (72)

07/06 -> 13/06 (72)

14/06 -> 20/06 (72)

21/06 -> 27/06 (72-50=22)

28/06 -> 04/07 (72-3=69)

05/07 -> 11/07 (72)

12/07 -> 18/07 (72)

19/07 -> 25/07 (72-3=69)

26/07 -> 01/08 (72-50=22)

02/08 -> 08/08 (72)

09/08 -> 15/08 (72)

16/08 -> 22/08 (72-3=69)

23/08 -> 29/08 (72-50=22)

30/08 -> 05/09 (72)

06/09 -> 12/09 (72)

13/09 -> 19/09 (72)

20/09 -> 26/09 (72-50=22)

27/09 -> 03/10 (72-3=69)

####################################################################

package com.be.step.fxtrade.ebury.dao;


import com.be.step.fxtrade.ebury.constant.Constants;
import com.be.step.fxtrade.ebury.listener.FXTradeEburyTokenCacheExpiryListener;
import com.be.step.fxtrade.ebury.model.*;
import com.be.step.fxtrade.ebury.model.request.LoginRequest;
import com.be.step.fxtrade.ebury.model.request.QuoteRequest;
import com.be.step.fxtrade.ebury.model.request.TokenRequest;
import com.be.step.fxtrade.ebury.model.response.ClientsResponse;
import com.be.step.fxtrade.ebury.utility.FXTradeEburyAuthenticationUtil;
import com.be.step.fxtrade.ebury.utility.FXTradeEburyHazelcastUtil;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EburyCallService implements FXTradeEburyDao {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private FXTradeEburyTokenCacheExpiryListener fxTradeEburyTokenCacheExpiryListener;

    @Value("${ebury.auth.client.id}")
    private String authClientId;

    @Value("${ebury.auth.client.secret}")
    private String authClientSecret;

    @Value("${ebury.auth.grant.type}")
    private String grantType;

    @Value("${ebury.auth.state}")
    private String state;


    @Override
    public void login(String userId, String password) {
        log.debug("Starting the login request for the user [{}]", userId);

        // sends post request to the login URL to get the returned 'code' String to use on 'getToken' call
        final String code = FXTradeEburyAuthenticationUtil.login(new LoginRequest(authClientId, userId,
                                                                             password, state), restTemplate);

        // sends post request to get the 'token' JSON response, which contains both 'access token' and 'refresh token'
        final Token token = FXTradeEburyAuthenticationUtil.getToken(new TokenRequest(grantType, code,
                                Constants.TOKEN_REDIRECT_URL), authClientId, authClientSecret, restTemplate);

        // stores 'access token' into hazelcast
        FXTradeEburyHazelcastUtil.writeTokenToHazelcast(userId, token.getAccessToken(),
                Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance, fxTradeEburyTokenCacheExpiryListener, true);

        // stores 'refresh token' into hazelcast
        FXTradeEburyHazelcastUtil.writeTokenToHazelcast(userId, token.getRefreshToken(),
                Constants.EBURY_REFRESH_TOKEN_CACHE_NAME, hazelcastInstance, fxTradeEburyTokenCacheExpiryListener, false);

        log.debug("Login done successfully for the user [{}]", userId);
    }

    @Override
    public void logout(String userId) {
        log.debug("Starting the logout request for the user [{}]", userId);

        // removes the cached 'access token' for the current userId
        FXTradeEburyHazelcastUtil.removeTokenFromHazelcast(userId,
                Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance);

        // removes the cached 'refresh token' for the current userId
        FXTradeEburyHazelcastUtil.removeTokenFromHazelcast(userId,
                Constants.EBURY_REFRESH_TOKEN_CACHE_NAME, hazelcastInstance);

        log.debug("Logout done successfully for the user [{}]", userId);
    }

    @Override
    public List<ClientsResponse> getCompany(String jwt) {

        String url = "https://sandbox.ebury.io/clients";

        final HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", jwt);
        System.out.println("bearer : " + headers.get("Authorization"));
        final HttpEntity<?> request = new HttpEntity<>(null, headers);
        //restTemplate.setErrorHandler(new RestTemplateResponseErrorHandler());
        ResponseEntity<List<ClientsResponse>> clientsResponse = restTemplate.exchange(url,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<List<ClientsResponse>>() {
                });

        return clientsResponse.getBody();

    }

    @Override
    public List<Payment> getPaymentList(String clientId, Integer page, Integer pageSize,
                                String reference, String tradeId, String userId, String country, String companyId) {

        // after login, the access token is retrieved from Hazelcast in-memory cache
        final String accessToken = FXTradeEburyHazelcastUtil.readTokenFromHazelcast(userId,
                                        Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance);

        ResponseEntity<List<Payment>> paymentListResponse = null;
        try {
            final HttpHeaders headers = new HttpHeaders();
            if (accessToken != null) {
                headers.setBearerAuth(accessToken); // adds the access token to the Authorization Bearer header
                log.debug("getPaymentList :: Bearer: " + accessToken);

                headers.set("country", country);
                headers.set("company_id", companyId);
            }

            final HttpEntity<?> request = new HttpEntity<>(null, headers);
            final String url = Constants.GET_PAYMENT_LIST_URL;
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("client_id", clientId);

            if (page != null) {
                builder = builder.queryParam("page", page); // field not required
            }

            if (pageSize != null) {
                builder = builder.queryParam("page_size", pageSize); // field not required
            }

            if (StringUtils.hasLength(reference)) {
                builder = builder.queryParam("reference", reference); // field not required
            }

            if (StringUtils.hasLength(tradeId)) {
                builder = builder.queryParam("trade_id", tradeId); // field not required
            }

            UriComponents uriComponents = builder.build();

            paymentListResponse = restTemplate.exchange(uriComponents.toString(),
                        HttpMethod.GET, request, new ParameterizedTypeReference<List<Payment>>() {});
        } catch (Exception ex) {
            log.error("getPaymentList :: ERROR - " + ex.getMessage());
            throw ex; // login was not done before this call
        }

        return paymentListResponse.getBody();
    }

    @Override
    public Payment getPayment(String paymentId, String clientId, String userId, String country, String companyId) {

        // after login, the access token is retrieved from Hazelcast in-memory cache
        final String accessToken = FXTradeEburyHazelcastUtil.readTokenFromHazelcast(userId,
                                        Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance);

        ResponseEntity<Payment> paymentResponse = null;
        try {
            final HttpHeaders headers = new HttpHeaders();
            if (accessToken != null) {
                headers.setBearerAuth(accessToken); // adds the access token to the Authorization Bearer header
                log.debug("getPayment :: Bearer: " + accessToken);

                headers.set("country", country);
                headers.set("company_id", companyId);
            }

            final Map<String, String> pathParam = new HashMap<>();
            pathParam.put("payment_id", paymentId);

            final String url = Constants.GET_PAYMENT_BY_ID_URL;
            final HttpEntity<?> request = new HttpEntity<>(null, headers);
            final UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url)
                                            .queryParam("client_id", clientId).build();

            paymentResponse = restTemplate.exchange(uriComponents.toString(),
                    HttpMethod.GET, request, new ParameterizedTypeReference<Payment>() {}, pathParam);
        } catch (Exception ex) {
            log.error("getPayment :: ERROR - " + ex.getMessage());
            throw ex; // login was not done before this call
        }

        return paymentResponse.getBody();
    }

    @Override
    public List<PaymentFee> getEstimatePaymentFeeList(String clientId, String paymentAmount, String paymentCurrency,
                    String paymentCountry, Boolean paymentIntraEbury, String userId, String country, String companyId) {

        // after login, the access token is retrieved from Hazelcast in-memory cache
        final String accessToken = FXTradeEburyHazelcastUtil.readTokenFromHazelcast(userId,
                                        Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance);

        ResponseEntity<List<PaymentFee>> paymentFeeListResponse = null;
        try {
            final HttpHeaders headers = new HttpHeaders();
            if (accessToken != null) {
                headers.setBearerAuth(accessToken); // adds the access token to the Authorization Bearer header
                log.debug("getEstimatePaymentFee :: Bearer: " + accessToken);

                headers.set("country", country);
                headers.set("company_id", companyId);
            }

            final HttpEntity<?> request = new HttpEntity<>(null, headers);
            final String url = Constants.GET_ESTIMATE_PAYMENT_FEE_URL;
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("client_id", clientId)
                    .queryParam("payment_amount", paymentAmount)
                    .queryParam("payment_currency", paymentCurrency);

            if (StringUtils.hasLength(paymentCountry)) {
                builder = builder.queryParam("payment_country", paymentCountry); // field not required
            }

            if (paymentIntraEbury != null && paymentIntraEbury) { // if the value is true
                builder = builder.queryParam("payment_intra_Ebury", paymentIntraEbury); // field not required
            }

            UriComponents uriComponents = builder.build();

            paymentFeeListResponse = restTemplate.exchange(uriComponents.toString(),
                    HttpMethod.GET, request, new ParameterizedTypeReference<List<PaymentFee>>() {});
        } catch (Exception ex) {
            log.error("getEstimatePaymentFeeList :: ERROR - " + ex.getMessage());
            throw ex;
        }

        return paymentFeeListResponse.getBody();
    }

    @Override
    public Quote getQuote(String clientId, String quoteType, String userId, QuoteRequest quoteRequest) {

        // after login, the access token is retrieved from Hazelcast in-memory cache
        final String accessToken = FXTradeEburyHazelcastUtil.readTokenFromHazelcast(userId,
                                        Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance);

        ResponseEntity<Quote> quoteResponse = null;
        try {
            final HttpHeaders headers = new HttpHeaders();
            if (accessToken != null) {
                headers.setBearerAuth(accessToken);
                log.debug("getQuote :: Bearer: " + accessToken);
            }

            final HttpEntity<?> request = new HttpEntity<>(quoteRequest, headers);
            final String url = Constants.GET_QUOTE_URL;

            final UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("client_id", clientId)
                    .queryParam("quote_type", quoteType).build();

            quoteResponse = restTemplate.exchange(uriComponents.toString(),
                    HttpMethod.POST, request, new ParameterizedTypeReference<Quote>() {});
        } catch (Exception ex) {
            log.error("getQuote :: ERROR - " + ex.getMessage());
            throw ex; // login was not done before this call
        }

        return quoteResponse.getBody();
    }

    @Override
    public List<Trade> getTradeList(String clientId, String tradeType, String parentTradeId,
                                    Integer page, Integer pageSize, String userId, String country, String companyId) {

        // after login, the access token is retrieved from Hazelcast in-memory cache
        final String accessToken = FXTradeEburyHazelcastUtil.readTokenFromHazelcast(userId,
                Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance);

        ResponseEntity<List<Trade>> tradeListResponse = null;
        try {
            final HttpHeaders headers = new HttpHeaders();
            if (accessToken != null) {
                headers.setBearerAuth(accessToken);
                log.debug("getTradeList :: Bearer: " + accessToken);

                headers.set("country", country);
                headers.set("company_id", companyId);
            }

            final HttpEntity<?> request = new HttpEntity<>(null, headers);
            final String url = Constants.GET_TRADE_LIST_URL;
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("client_id", clientId);

            if (StringUtils.hasLength(tradeType)) {
                builder = builder.queryParam("trade_type", tradeType); // field not required
            }

            if (StringUtils.hasLength(parentTradeId)) {
                builder = builder.queryParam("parent_trade_id", parentTradeId); // field not required
            }

            if (page != null) {
                builder = builder.queryParam("page", page); // field not required
            }

            if (pageSize != null) {
                builder = builder.queryParam("page_size", pageSize); // field not required
            }

            UriComponents uriComponents = builder.build();

            tradeListResponse = restTemplate.exchange(uriComponents.toString(),
                    HttpMethod.GET, request, new ParameterizedTypeReference<List<Trade>>() {});
        } catch (Exception ex) {
            log.error("getTradeList :: ERROR - " + ex.getMessage());
            throw ex;
        }

        return tradeListResponse.getBody();
    }

    @Override
    public Trade getTrade(String tradeId, String clientId, String userId, String country, String companyId) {

        // after login, the access token is retrieved from Hazelcast in-memory cache
        final String accessToken = FXTradeEburyHazelcastUtil.readTokenFromHazelcast(userId,
                Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance);

        ResponseEntity<Trade> tradeResponse = null;
        try {
            final HttpHeaders headers = new HttpHeaders();
            if (accessToken != null) {
                headers.setBearerAuth(accessToken);
                log.debug("getTrade :: Bearer: " + accessToken);

                headers.set("country", country);
                headers.set("company_id", companyId);
            }

            final Map<String, String> pathParam = new HashMap<>();
            pathParam.put("trade_id", tradeId);

            final String url = Constants.GET_TRADE_BY_ID_URL;
            final HttpEntity<?> request = new HttpEntity<>(null, headers);
            final UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url)
                                            .queryParam("client_id", clientId).build();

            tradeResponse = restTemplate.exchange(uriComponents.toString(),
                    HttpMethod.GET, request, new ParameterizedTypeReference<Trade>() {}, pathParam);
        } catch (Exception ex) {
            log.error("getTrade :: ERROR - " + ex.getMessage());
            throw ex;
        }

        return tradeResponse.getBody();
    }
}

#######################################################################

package com.be.step.fxtrade.ebury.utility;

import com.be.step.fxtrade.ebury.constant.Constants;
import com.be.step.fxtrade.ebury.listener.FXTradeEburyTokenCacheExpiryListener;
import com.be.step.fxtrade.ebury.model.Token;
import com.be.step.fxtrade.ebury.model.request.LoginRequest;
import com.be.step.fxtrade.ebury.model.request.TokenRequest;
import com.hazelcast.core.HazelcastInstance;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.util.Base64;

public class FXTradeEburyAuthenticationUtil {

    public static String login(@Valid LoginRequest request, RestTemplate restTemplate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestEntityMap = new LinkedMultiValueMap<>();
        requestEntityMap.add("client_id", request.getClientId());
        requestEntityMap.add("email", request.getEmail());
        requestEntityMap.add("password", request.getPassword());
        requestEntityMap.add("state", request.getState());

        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(requestEntityMap, headers);
        ResponseEntity<String> loginResponse = restTemplate.exchange(Constants.LOGIN_URL, HttpMethod.POST, requestEntity,
                new ParameterizedTypeReference<String>() {});

        return getLoginCodeFromHtml(loginResponse.getBody());
    }

    public static Token getToken(@Valid TokenRequest request, String authClientId, String authClientSecret, RestTemplate restTemplate) {
        String credentials = authClientId + ":" + authClientSecret;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(Base64.getEncoder().encodeToString(credentials.getBytes()));

        MultiValueMap<String, String> requestEntityMap = new LinkedMultiValueMap<>();
        requestEntityMap.add("grant_type", request.getGrantType());
        requestEntityMap.add("code", request.getCode());
        requestEntityMap.add("redirect_uri", request.getRedirectUri());

        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(requestEntityMap, headers);
        ResponseEntity<Token> tokenResponse = restTemplate.exchange(Constants.GET_TOKEN_URL, HttpMethod.POST, requestEntity,
                new ParameterizedTypeReference<Token>() {});

        return tokenResponse.getBody();
    }

    public static Token refreshToken(String refreshToken, String authClientId, String authClientSecret, RestTemplate restTemplate) {
        String credentials = authClientId + ":" + authClientSecret;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(Base64.getEncoder().encodeToString(credentials.getBytes()));

        MultiValueMap<String, String> requestEntityMap = new LinkedMultiValueMap<>();
        requestEntityMap.add("grant_type", "refresh_token");
        requestEntityMap.add("refresh_token", refreshToken);
        requestEntityMap.add("scope", "openid");

        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(requestEntityMap, headers);
        ResponseEntity<Token> tokenResponse = restTemplate.exchange(Constants.GET_TOKEN_URL, HttpMethod.POST, requestEntity,
                new ParameterizedTypeReference<Token>() {});

        return tokenResponse.getBody();
    }

    public static void handleRefreshTokenAfterTimeToLive(String username, String authClientId, String authClientSecret,
                            RestTemplate restTemplate, HazelcastInstance hazelcastInstance, FXTradeEburyTokenCacheExpiryListener listener) {

        String refreshToken = FXTradeEburyHazelcastUtil.readTokenFromHazelcast(username,
                Constants.EBURY_REFRESH_TOKEN_CACHE_NAME, hazelcastInstance);

        // after login, if the call returns an exception of type "UNAUTHORIZED" it means that refresh token must be called
        Token newAccessToken = FXTradeEburyAuthenticationUtil.refreshToken(refreshToken,
                authClientId, authClientSecret, restTemplate);

        // stores the new 'access token' into hazelcast
        FXTradeEburyHazelcastUtil.writeTokenToHazelcast(username, newAccessToken.getAccessToken(),
                Constants.EBURY_ACCESS_TOKEN_CACHE_NAME, hazelcastInstance, listener, false);

        // stores the new 'refresh token' into hazelcast
        FXTradeEburyHazelcastUtil.writeTokenToHazelcast(username, newAccessToken.getRefreshToken(),
                Constants.EBURY_REFRESH_TOKEN_CACHE_NAME, hazelcastInstance, listener, false);
    }

    private static String getLoginCodeFromHtml(String htmlString) {
        Elements linkElement = Jsoup.parse(htmlString).select("a");
        String linkText = linkElement.text();
        String loginCode = linkText.substring(linkText.indexOf("code=") + 5, linkText.indexOf("&"));
        return loginCode;
    }

}

########################################################################

package com.be.step.fxtrade.ebury.listener;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.listener.EntryEvictedListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.be.step.fxtrade.ebury.utility.FXTradeEburyAuthenticationUtil;

@Slf4j
@Component
public class FXTradeEburyTokenCacheExpiryListener implements EntryEvictedListener<String, String> {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Value("${ebury.auth.client.id}")
    private String authClientId;

    @Value("${ebury.auth.client.secret}")
    private String authClientSecret;


    @Override
    public void entryEvicted(EntryEvent<String, String> entryEvent) {
        log.debug("Entry evicted: " + entryEvent);

        FXTradeEburyAuthenticationUtil.handleRefreshTokenAfterTimeToLive(entryEvent.getKey(), authClientId,
                                                        authClientSecret, restTemplate, hazelcastInstance, this);
    }
}

########################################################

Buongiorno,

vorrei informati che l'Agenzia dell'Entrate è tornata da me ancora con una nuova lettera dopo 4 anni (potete trovate la lettera in allegato).
Ho preso un appuntamento e sono andato nel loro sportello per chiedere spiegazioni. Mi hanno risposto che nel 2017 ho lavorato in 2 società, ma i versamenti dei contributi fatti non sono corretti.
Cioè, facendo i conti manca la somma di 200 che adesso è diventata 300 a causa della multa.
Hanno detto che se le due società si conoscessero, avrebbero potuto fare il conguaglio dei versamenti per evitare questo debito.

Però senza alcuna intenzione di litigare o di fare polemiche, la verità è che non va bene affatto registrare il nuovo contratto di lavoro senza farmi firmarlo prima. 

La domanda che faccio è: come è statto possibile per voi registrare il nuovo contratto di lavoro senza essere firmato da me? Senza dirmi niente? Ed è per questo che sono finiti dentro le giornate di trasferta, ma nessuno mi ha parlato delle transferte prima. Cioè, non ci siamo mai messi d'accordo per includere le trasferte nello stipendo.
Io vi ho detto all'inizio qual è il mio stipendio mensile netto, perché tutto è basato sulle tarife standard del mercato di sviluppo software e consulenza informatica, ed è comisurato all'esperinza lavorativa dello sviluppatore. 

Quindi se la trasferta è un'opzione che conviene per voi, bisognerebbe gestirla in automatico in modo che siano sempre pari a 26 giornate a prescindere dei giorni festivi o chiusura aziendale, ecc. visto che è un'opzione scelta da voi senza parlarmi di niente.

Nel mese di Dicembre 2023 sono rimasto fregato di 10 giornate in meno di trasferta a causa dei giorni festivi, della chiusura aziendale presso il cliente, più una giornata di permesso che ho preso.

Può sembrare che lo stipendio è alto, ma dipende da quanto si spende. Cioè, ho fatto circa 3 anni a pagare l'albergo ogni notte per dormire per poter andare a lavorare ogni mattina. Quindi dal 2017 al 2020 prendevo lo stipendio per pagare albergui e dormitori a Milano perché a causa dei pregiudizi nessuno accetta affittare la casa alle persone che considerano chissà cosa.

########################################################

