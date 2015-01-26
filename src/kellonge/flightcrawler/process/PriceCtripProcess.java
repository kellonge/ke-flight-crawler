package kellonge.flightcrawler.process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import kellonge.flightcrawler.config.Configuration;
import kellonge.flightcrawler.model.FlightInfo;
import kellonge.flightcrawler.model.FlightPrice;
import kellonge.flightcrawler.utils.DateTimeUtils;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.proxy.ProxyPool;

public class PriceCtripProcess implements PageProcessor {

	public PriceCtripProcess() {
		List<String[]> httpProxyList = new ArrayList<String[]>();
		httpProxyList.addAll(Configuration.getProxys());
		if (Configuration.isUseProxy()) {
			ProxyPool proxyPool = site.enableHttpProxyPool().getHttpProxyPool();
			proxyPool.validateWhenInit(true);
			proxyPool.setWebGet(true);
			proxyPool
					.setMinProxy((int)Math.round(Configuration.getThreadNum() * 1.2));
			proxyPool.addProxy(httpProxyList.toArray(new String[httpProxyList
					.size()][]));
			proxyPool.checkAndGetProxyFromWeb();
			proxyPool.setProxyFilePath(Configuration.ROOT_PATH
					+ "/data/lastUse.proxy");
		}

	}

	private Site site = Site.me()
			.setCycleRetryTimes(Configuration.getCycleRetryTimes())
			.setSleepTime(Configuration.getSleepTime())
			.setTimeOut(Configuration.getTimeOut());

	@Override
	public void process(Page page) {
		if (page.getUrl().regex("flights.ctrip.com/booking").match()) {
			Page multiRequestPage = (Page) page;
			String rand = page.getHtml().regex("ajaxRequest\\(url.*\'\\);")
					.regex("\\d\\.\\d+").get();
			String url = page
					.getHtml()
					.regex("http://flights.ctrip.com/domesticsearch/search/SearchFirstRouteFlights.*CK=[0-9A-z]+")
					.get();
			url += "&r=" + rand;
			page.getRequest().setUrl(url);
			page.getRequest().setFinsih(false);
			Map<String, String> heads = page.getRequest().getRequestHeads();
			if (heads == null) {
				heads = new HashMap<String, String>();
			}
			heads.put("Referer", page.getRequest().getInitUrl());
			page.getRequest().setRequestHeads(heads);
		} else {
			page.getRequest().setFinsih(true);
			JSONObject jsonObject = JSONObject.parseObject(page.getRawText());
			JSONArray flightJosnArray = jsonObject.getJSONArray("fis");
			List<FlightInfo> flightInfos = new ArrayList<FlightInfo>();
			if (flightJosnArray != null) {
				for (int i = 0; i < flightJosnArray.size(); i++) {
					JSONObject flightJson = flightJosnArray.getJSONObject(i);
					FlightInfo flightInfo = new FlightInfo();
					flightInfo.setAirLineCode(flightJson.getString("alc"));
					flightInfo.setAdditionalFee(BigDecimal.valueOf(flightJson
							.getDoubleValue("of")
							+ flightJson.getDoubleValue("tax")));
					Date arrDate = flightJson.getDate("at");
					Date deptDate = flightJson.getDate("dt");
					flightInfo.setFlightDate(deptDate);
					flightInfo.setDeptTime(DateTimeUtils
							.parseTime(DateTimeUtils.format(deptDate,
									"HH:mm:ss")));
					flightInfo.setArrTime(DateTimeUtils.parseTime(DateTimeUtils
							.format(arrDate, "HH:mm:ss")));
					flightInfo.setAheadDay(Math.abs(DateTimeUtils
							.countDays(deptDate)) + 1);
					flightInfo.setFlightInterval(DateTimeUtils.SubstractTime(
							flightInfo.getDeptTime(), flightInfo.getArrTime()));
					flightInfo.setArrCityCode(flightJson.getString("acc"));
					flightInfo.setDeptCityCode(flightJson.getString("dcc"));
					flightInfo.setArrAirportCode(flightJson.getString("apc"));
					flightInfo.setDeptAirportCode(flightJson.getString("dpc"));
					flightInfo.setDataSource("CTRIP");
					flightInfo.setFlag(1);
					flightInfo.setFlightNo(flightJson.getString("fn"));
					flightInfo.setPlanModel(flightJson.getJSONObject("cf")
							.getString("c"));
					flightInfo.setPunctualityRate(flightJson.getFloat("pr"));
					JSONArray flightPriceJsonArray = flightJson
							.getJSONArray("scs");
					List<FlightPrice> flightPrices = new ArrayList<FlightPrice>();
					for (int j = 0; j < flightPriceJsonArray.size(); j++) {
						JSONObject flightPriceJson = flightPriceJsonArray
								.getJSONObject(j);
						FlightPrice flightPrice = new FlightPrice();
						flightPrice.setPrice(BigDecimal.valueOf(flightPriceJson
								.getDouble("p")));
						flightPrice.setCabin1(flightPriceJson.getString("c"));
						flightPrices.add(flightPrice);
					}
					flightInfo.setFlightPrices(flightPrices);
					flightInfos.add(flightInfo);
				}
			}

			if (flightInfos.size() == 0) {
				page.setPageBizError(true);
				page.setSkip(true);
			} else {
				page.putField("ModelData", flightInfos);
				page.putField("Request", page.getRequest());
				page.getRequest().putExtra(Request.BIZSUCCESS, 1);
			}
		}
	}

	@Override
	public Site getSite() {
		return site;
	}

}
