/*
 * Copyright (c) 2021-2031, 河北计全科技有限公司 (https://www.jeequan.com & jeequan@126.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jeequan.jeepay.pay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.IsvInfo;
import com.jeequan.jeepay.core.entity.MchApp;
import com.jeequan.jeepay.core.entity.MchInfo;
import com.jeequan.jeepay.core.entity.PayInterfaceConfig;
import com.jeequan.jeepay.core.model.params.IsvParams;
import com.jeequan.jeepay.core.model.params.IsvsubMchParams;
import com.jeequan.jeepay.core.model.params.NormalMchParams;
import com.jeequan.jeepay.core.model.params.alipay.AlipayConfig;
import com.jeequan.jeepay.core.model.params.alipay.AlipayIsvParams;
import com.jeequan.jeepay.core.model.params.alipay.AlipayNormalMchParams;
import com.jeequan.jeepay.core.model.params.wxpay.WxpayIsvParams;
import com.jeequan.jeepay.core.model.params.wxpay.WxpayNormalMchParams;
import com.jeequan.jeepay.pay.model.*;
import com.jeequan.jeepay.pay.util.ChannelCertConfigKitBean;
import com.jeequan.jeepay.service.impl.IsvInfoService;
import com.jeequan.jeepay.service.impl.MchAppService;
import com.jeequan.jeepay.service.impl.MchInfoService;
import com.jeequan.jeepay.service.impl.PayInterfaceConfigService;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
* 商户/服务商 配置信息上下文服务
*
* @author terrfly
* @site https://www.jeepay.vip
* @date 2021/6/8 17:41
*/
@Service
public class ConfigContextService {

    /** <商户ID, 商户配置项>  **/
    private static final Map<String, MchInfoConfigContext> mchInfoConfigContextMap = new ConcurrentHashMap<>();

    /** <应用ID, 商户配置上下文>  **/
    private static final Map<String, MchAppConfigContext> mchAppConfigContextMap = new ConcurrentHashMap<>();

    /** <服务商号, 服务商配置上下文>  **/
    private static final Map<String, IsvConfigContext> isvConfigContextMap = new ConcurrentHashMap<>();

    @Autowired private MchInfoService mchInfoService;
    @Autowired private MchAppService mchAppService;
    @Autowired private IsvInfoService isvInfoService;
    @Autowired private PayInterfaceConfigService payInterfaceConfigService;
    @Autowired private ChannelCertConfigKitBean channelCertConfigKitBean;


    /** 获取 [商户配置信息] **/
    public synchronized MchInfoConfigContext getMchInfoConfigContext(String mchNo){

        MchInfoConfigContext mchInfoConfigContext = mchInfoConfigContextMap.get(mchNo);

        //无此数据， 需要初始化
        if(mchInfoConfigContext == null){
            initMchInfoConfigContext(mchNo);
        }

        return mchInfoConfigContextMap.get(mchNo);
    }

    /** 获取 [商户应用支付参数配置信息] **/
    public synchronized MchAppConfigContext getMchAppConfigContext(String mchNo, String appId){

        MchAppConfigContext mchAppConfigContext = mchAppConfigContextMap.get(appId);

        //无此数据， 需要初始化
        if(mchAppConfigContext == null){
            initMchAppConfigContext(mchNo, appId);
        }

        return mchAppConfigContextMap.get(appId);
    }

    /** 获取 [ISV支付参数配置信息] **/
    public synchronized IsvConfigContext getIsvConfigContext(String isvNo){

        IsvConfigContext isvConfigContext = isvConfigContextMap.get(isvNo);

        //无此数据， 需要初始化
        if(isvConfigContext == null){
            initIsvConfigContext(isvNo);
        }

        return isvConfigContextMap.get(isvNo);
    }


    /** 初始化 [商户配置信息] **/
    public synchronized void initMchInfoConfigContext(String mchNo){

        //商户主体信息
        MchInfo mchInfo = mchInfoService.getById(mchNo);
        if(mchInfo == null){ // 查询不到商户主体， 可能已经删除

            MchInfoConfigContext mchInfoConfigContext = mchInfoConfigContextMap.get(mchNo);

            // 删除所有的商户应用
            if(mchInfoConfigContext != null){
                mchInfoConfigContext.getAppMap().forEach((k, v) -> mchAppConfigContextMap.remove(k));
            }

            mchInfoConfigContextMap.remove(mchNo);
            return ;
        }

        MchInfoConfigContext mchInfoConfigContext = new MchInfoConfigContext();

        // 设置商户信息
        mchInfoConfigContext.setMchNo(mchInfo.getMchNo());
        mchInfoConfigContext.setMchType(mchInfo.getType());
        mchInfoConfigContext.setMchInfo(mchInfo);
        mchAppService.list(MchApp.gw().eq(MchApp::getMchNo, mchNo)).stream().forEach( mchApp -> mchInfoConfigContext.putMchApp(mchApp));

        mchInfoConfigContextMap.put(mchNo, mchInfoConfigContext);
    }

    /** 初始化 [商户应用支付参数配置信息] **/
    public synchronized void initMchAppConfigContext(String mchNo, String appId){

        // 获取商户的配置信息
        MchInfoConfigContext mchInfoConfigContext = getMchInfoConfigContext(mchNo);
        if(mchInfoConfigContext == null){ // 商户信息不存在
            return;
        }

        //DB已经删除
        if(mchAppService.count(MchApp.gw().eq(MchApp::getAppId, appId)) <= 0){
            mchAppConfigContextMap.remove(appId);  //清除缓存信息
            mchInfoConfigContext.getAppMap().remove(appId); //清除主体信息中的appId
            return ;
        }

        //商户应用信息(缓存中存在)
        MchApp mchApp = mchInfoConfigContext.getMchApp(appId);

        if(mchApp == null){ //说明商户主体信息不存在缓存

            mchApp = mchAppService.getById(appId);
            if(mchApp == null){ // DB查询为空
                mchAppConfigContextMap.remove(appId);  //清除缓存信息
                mchInfoConfigContext.getAppMap().remove(appId); //清除主体信息中的appId
                return ;
            }

            //更新商户信息主体中的商户应用
            mchInfoConfigContext.putMchApp(mchApp);
        }

        //商户主体信息
        MchInfo mchInfo = mchInfoConfigContext.getMchInfo();
        MchAppConfigContext mchAppConfigContext = new MchAppConfigContext();

        // 设置商户信息
        mchAppConfigContext.setAppId(appId);
        mchAppConfigContext.setMchNo(mchInfo.getMchNo());
        mchAppConfigContext.setMchType(mchInfo.getType());
        mchAppConfigContext.setMchInfo(mchInfo);
        mchAppConfigContext.setMchApp(mchApp);

        // 查询商户的所有支持的参数配置
        List<PayInterfaceConfig> allConfigList = payInterfaceConfigService.list(PayInterfaceConfig.gw()
                .select(PayInterfaceConfig::getIfCode, PayInterfaceConfig::getIfParams)
                .eq(PayInterfaceConfig::getState, CS.YES)
                .eq(PayInterfaceConfig::getInfoType, CS.INFO_TYPE_MCH_APP)
                .eq(PayInterfaceConfig::getInfoId, appId)
        );

        // 普通商户
        if(mchInfo.getType() == CS.MCH_TYPE_NORMAL){

            for (PayInterfaceConfig payInterfaceConfig : allConfigList) {
                mchAppConfigContext.getNormalMchParamsMap().put(
                        payInterfaceConfig.getIfCode(),
                        NormalMchParams.factory(payInterfaceConfig.getIfCode(), payInterfaceConfig.getIfParams())
                );
            }

            //放置alipay client

            AlipayNormalMchParams alipayParams = mchAppConfigContext.getNormalMchParamsByIfCode(CS.IF_CODE.ALIPAY, AlipayNormalMchParams.class);
            if(alipayParams != null){

                mchAppConfigContext.setAlipayClientWrapper(buildAlipayClientWrapper(
                        alipayParams.getUseCert(), alipayParams.getSandbox(), alipayParams.getAppId(), alipayParams.getPrivateKey(),
                        alipayParams.getAlipayPublicKey(), alipayParams.getSignType(), alipayParams.getAppPublicCert(),
                        alipayParams.getAlipayPublicCert(), alipayParams.getAlipayRootCert()
                        )
                );

            }

            //放置 wxJavaService
            WxpayNormalMchParams wxpayParams = mchAppConfigContext.getNormalMchParamsByIfCode(CS.IF_CODE.WXPAY, WxpayNormalMchParams.class);
            if(wxpayParams != null){
                mchAppConfigContext.setWxServiceWrapper(buildWxServiceWrapper(wxpayParams.getMchId(), wxpayParams.getAppId(),
                        wxpayParams.getAppSecret(), wxpayParams.getKey(), wxpayParams.getApiVersion(), wxpayParams.getApiV3Key(),
                        wxpayParams.getSerialNo(), wxpayParams.getCert(), wxpayParams.getApiClientKey()));
            }


        }else{ //服务商模式商户
            for (PayInterfaceConfig payInterfaceConfig : allConfigList) {
                mchAppConfigContext.getIsvsubMchParamsMap().put(
                        payInterfaceConfig.getIfCode(),
                        IsvsubMchParams.factory(payInterfaceConfig.getIfCode(), payInterfaceConfig.getIfParams())
                );
            }

            //放置 当前商户的 服务商信息
            mchAppConfigContext.setIsvConfigContext(getIsvConfigContext(mchInfo.getIsvNo()));

        }

        mchAppConfigContextMap.put(appId, mchAppConfigContext);
    }


    /** 初始化 [ISV支付参数配置信息]  **/
    public synchronized void initIsvConfigContext(String isvNo){

        IsvConfigContext isvConfigContext = new IsvConfigContext();
        IsvInfo isvInfo = isvInfoService.getById(isvNo);
        if(isvInfo == null){

            //查询出所有商户的配置信息并更新
            mchInfoService.list(MchInfo.gw().select(MchInfo::getMchNo).eq(MchInfo::getIsvNo, isvNo)).forEach(mchInfoItem -> {

                //将更新已存在缓存的商户配置信息 （每个商户下存储的为同一个 服务商配置的对象指针）
                MchAppConfigContext mchAppConfigContext = mchAppConfigContextMap.get(mchInfoItem.getMchNo());
                if(mchAppConfigContext != null){
                    mchAppConfigContext.setIsvConfigContext(null);
                }
            });
            isvConfigContextMap.remove(isvNo); // 服务商有商户不可删除， 此处不再更新商户下的配置信息
            return ;
        }

        // 设置商户信息
        isvConfigContext.setIsvNo(isvInfo.getIsvNo());
        isvConfigContext.setIsvInfo(isvInfo);

        // 查询商户的所有支持的参数配置
        List<PayInterfaceConfig> allConfigList = payInterfaceConfigService.list(PayInterfaceConfig.gw()
                .select(PayInterfaceConfig::getIfCode, PayInterfaceConfig::getIfParams)
                .eq(PayInterfaceConfig::getState, CS.YES)
                .eq(PayInterfaceConfig::getInfoType, CS.INFO_TYPE_ISV)
                .eq(PayInterfaceConfig::getInfoId, isvNo)
        );

        for (PayInterfaceConfig payInterfaceConfig : allConfigList) {
            isvConfigContext.getIsvParamsMap().put(
                    payInterfaceConfig.getIfCode(),
                    IsvParams.factory(payInterfaceConfig.getIfCode(), payInterfaceConfig.getIfParams())
            );
        }

        //放置alipay client
        AlipayIsvParams alipayParams = isvConfigContext.getIsvParamsByIfCode(CS.IF_CODE.ALIPAY, AlipayIsvParams.class);
        if(alipayParams != null){
            isvConfigContext.setAlipayClientWrapper(buildAlipayClientWrapper(
                    alipayParams.getUseCert(), alipayParams.getSandbox(), alipayParams.getAppId(), alipayParams.getPrivateKey(),
                    alipayParams.getAlipayPublicKey(), alipayParams.getSignType(), alipayParams.getAppPublicCert(),
                    alipayParams.getAlipayPublicCert(), alipayParams.getAlipayRootCert()
                    )
            );
        }

        //放置 wxJavaService
        WxpayIsvParams wxpayParams = isvConfigContext.getIsvParamsByIfCode(CS.IF_CODE.WXPAY, WxpayIsvParams.class);
        if(wxpayParams != null){
            isvConfigContext.setWxServiceWrapper(buildWxServiceWrapper(wxpayParams.getMchId(), wxpayParams.getAppId(),
                    wxpayParams.getAppSecret(), wxpayParams.getKey(), wxpayParams.getApiVersion(), wxpayParams.getApiV3Key(),
                    wxpayParams.getSerialNo(), wxpayParams.getCert(), wxpayParams.getApiClientKey()));
        }

        isvConfigContextMap.put(isvNo, isvConfigContext);

        //查询出所有商户的配置信息并更新
        mchInfoService.list(MchInfo.gw().select(MchInfo::getMchNo).eq(MchInfo::getIsvNo, isvNo)).forEach(mchInfoItem -> {

            //将更新已存在缓存的商户配置信息 （每个商户下存储的为同一个 服务商配置的对象指针）
            MchAppConfigContext mchAppConfigContext = mchAppConfigContextMap.get(mchInfoItem.getMchNo());
            if(mchAppConfigContext != null){
                mchAppConfigContext.setIsvConfigContext(isvConfigContext);
            }

        });

    }


    /*
    * 构建支付宝client 包装类
    *
    * @author terrfly
    * @site https://www.jeepay.vip
    * @date 2021/6/8 17:46
    */
    private AlipayClientWrapper buildAlipayClientWrapper(Byte useCert, Byte sandbox, String appId, String privateKey, String alipayPublicKey, String signType, String appCert,
                                                         String alipayPublicCert, String alipayRootCert){

        //避免空值
        sandbox = sandbox == null ? CS.NO : sandbox;

        AlipayClient alipayClient = null;
        if(useCert != null && useCert == CS.YES){ //证书的方式

            CertAlipayRequest certAlipayRequest = new CertAlipayRequest();
            certAlipayRequest.setServerUrl(sandbox == CS.YES ? AlipayConfig.SANDBOX_SERVER_URL : AlipayConfig.PROD_SERVER_URL);
            certAlipayRequest.setAppId(appId);
            certAlipayRequest.setPrivateKey(privateKey);
            certAlipayRequest.setFormat(AlipayConfig.FORMAT);
            certAlipayRequest.setCharset(AlipayConfig.CHARSET);
            certAlipayRequest.setSignType(signType);

            certAlipayRequest.setCertPath(channelCertConfigKitBean.getCertFilePath(appCert));
            certAlipayRequest.setAlipayPublicCertPath(channelCertConfigKitBean.getCertFilePath(alipayPublicCert));
            certAlipayRequest.setRootCertPath(channelCertConfigKitBean.getCertFilePath(alipayRootCert));
            try {
                alipayClient = new DefaultAlipayClient(certAlipayRequest);
            } catch (AlipayApiException e) {
                e.printStackTrace();
            }
        }else{
            alipayClient = new DefaultAlipayClient(sandbox == CS.YES ? AlipayConfig.SANDBOX_SERVER_URL : AlipayConfig.PROD_SERVER_URL
                    , appId, privateKey, AlipayConfig.FORMAT, AlipayConfig.CHARSET,
                    alipayPublicKey, signType);
        }

        return new AlipayClientWrapper(useCert, alipayClient);
    }

    private WxServiceWrapper buildWxServiceWrapper(String mchId, String appId, String appSecret, String mchKey, String apiVersion, String apiV3Key,
                                                            String serialNo, String cert, String apiClientKey){

        WxPayConfig wxPayConfig = new WxPayConfig();
        wxPayConfig.setMchId(mchId);
        wxPayConfig.setAppId(appId);
        wxPayConfig.setMchKey(mchKey);

        if (CS.PAY_IF_VERSION.WX_V2.equals(apiVersion)) { // 微信API  V2
            wxPayConfig.setSignType(WxPayConstants.SignType.MD5);
        } else if (CS.PAY_IF_VERSION.WX_V3.equals(apiVersion)) { // 微信API  V3
            wxPayConfig.setApiV3Key(apiV3Key);
            wxPayConfig.setCertSerialNo(serialNo);
            wxPayConfig.setPrivateCertPath(channelCertConfigKitBean.getCertFilePath(cert));
            wxPayConfig.setPrivateKeyPath(channelCertConfigKitBean.getCertFilePath(apiClientKey));
        }

        WxPayService wxPayService = new WxPayServiceImpl();
        wxPayService.setConfig(wxPayConfig); //微信配置信息

        WxMpDefaultConfigImpl wxMpConfigStorage = new WxMpDefaultConfigImpl();
        wxMpConfigStorage.setAppId(appId);
        wxMpConfigStorage.setSecret(appSecret);

        WxMpService wxMpService = new WxMpServiceImpl();
        wxMpService.setWxMpConfigStorage(wxMpConfigStorage); //微信配置信息

        return new WxServiceWrapper(apiVersion, wxPayService, wxMpService);
    }

}
