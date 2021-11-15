package ru.steambot;


import com.google.gson.internal.$Gson$Preconditions;
import com.lukaspradel.steamapi.core.exception.SteamApiException;
import com.lukaspradel.steamapi.data.json.appnews.GetNewsForApp;
import com.lukaspradel.steamapi.data.json.appnews.Newsitem;
import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient;
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.Message;
import org.jsoup.*;
import org.jsoup.nodes.Element;

import java.util.*;

public class Main {
    private static final String STEAM_KEY = "55C6DEFA9022837C32E65FA432774F47";
    private static final String VK_KEY = "4d95b9aa21b50cca928e81713661c71588195d76bf0f0dfe05a6da15196fca41e839d409ca4d1d5891533";
    private static final Map<String, Integer> idMap = Map.of("dota2", 570, "csgo", 730,
            "pubg", 578080, "battlefield2024", 1517290, "reddeadredemption2", 1174180);
    private static $Gson$Preconditions Preconditions;

    private static GetNewsForApp getRequest(String game) throws SteamApiException {
        var id = idMap.get(game);
        var client = new SteamWebApiClient.SteamWebApiClientBuilder(STEAM_KEY).build();
        var request = SteamWebApiRequestFactory.createGetNewsForAppRequest(id);
        return client.processRequest(request);
    }

    private static List<String> splitInChunks(String s) {
        $Gson$Preconditions.checkArgument(4095 > 0);
        List<String> result = new ArrayList<String>
                ((s.length() + 4095 - 1) / 4095);
        int length = s.length();
        int dot_index = 0;
        for (int i = 0; i < length; i += 4095) {
            result.add(s.substring(i, Math.min(length, i + 4095)));
        }
        return result;
    }


    private static void htmlParseByP(Message message, Random random, GroupActor actor, String req, VkApiClient vk) throws SteamApiException, ClientException, ApiException {
        var getNewsForApp = getRequest(req);
        StringBuilder new_msg = new StringBuilder();
        getNewsForApp.getAppnews().getNewsitems()
                .stream().map(Newsitem::getContents)
                .forEach(text -> {
                    var doc = Jsoup.parse(text);
                    var paragraphs = doc.select("p");
                    for(Element p : paragraphs)
                        new_msg.append(p.text()).append("\n");
                });
                var new_str = String.valueOf(new_msg);
                var str_arr = splitInChunks(new_str);
                for (String p : str_arr) {
                    vk.messages().send(actor).message(p).userId(message.getFromId()).randomId(random.nextInt(100000)).execute();
                }
    }

    private static String removePunt(String str) {
        return str.replaceAll("[^a-zа-я0-9]", "");
    }

    public static void main(String[] args) throws ClientException, ApiException,  InterruptedException {
        var transportClient = new HttpTransportClient();
        var vk = new VkApiClient(transportClient);
        var random = new Random();
        var actor = new GroupActor(208748483,  VK_KEY);
        var ts = vk.messages().getLongPollServer(actor).execute().getTs();
        while (true) {
            var historyQuery = vk.messages().getLongPollHistory(actor).ts(ts);
            var messages = historyQuery.execute().getMessages().getItems();
            if (!messages.isEmpty()) {
                messages.forEach(message -> {
                    System.out.println(message.toString());
                    try {
                        var msg = removePunt(message.getText().toLowerCase());
                        if (msg.equals("привет")) {
                            vk.messages().send(actor).message(
                                    """
                                            Привет!
                                            Вот реализованные запросы, которыми ты можешь воспользоваться:
                                            Dota 2
                                            CS:GO
                                            PUBG
                                            BattleField 2024
                                            Red Dead Redemption 2
                                            Любой регистр и знаки препинания поддерживаются
                                                                                           """
                            ).userId(message.getFromId()).randomId(random.nextInt(10000)).execute();
                        } else if (idMap.containsKey(msg)) {
                            htmlParseByP(message, random, actor, msg, vk);
                        } else
                            vk.messages().send(actor).message("Я тебя не понимаю. Напиши Привет")
                            .userId(message.getFromId()).randomId(random.nextInt(10000)).execute();}
                    catch (ClientException | ApiException | SteamApiException e) {
                        e.printStackTrace();
                    }
                });
            }
            ts = vk.messages().getLongPollServer(actor).execute().getTs();
            Thread.sleep(1000);
        }
    }
}