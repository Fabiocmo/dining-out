/*
 * Copyright 2013-2015 pushbit <pushbit@gmail.com>
 *
 * This file is part of Dining Out.
 *
 * Dining Out is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Dining Out is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Dining Out. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package net.sf.diningout.net;

import android.util.Log;

import com.squareup.okhttp.OkHttpClient;

import net.sf.diningout.data.Init;
import net.sf.diningout.data.Restaurant;
import net.sf.diningout.data.Review;
import net.sf.diningout.data.Syncing;
import net.sf.diningout.data.User;
import net.sf.sprockets.preference.Prefs;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter.Builder;
import retrofit.RetrofitError;
import retrofit.client.Client;
import retrofit.client.OkClient;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;

import static net.sf.diningout.BuildConfig.SERVER_URL;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.INSTALL_ID;
import static net.sf.sprockets.app.SprocketsApplication.context;
import static net.sf.sprockets.gms.analytics.Trackers.exception;

/**
 * Methods for communicating with the server.
 */
public class Server {
    public static final int BACKOFF_RETRIES = 5;
    private static final String TAG = Server.class.getSimpleName();
    private static final Api API;

    static {
        try { // require the known server certificate when connecting
            KeyStore store = KeyStore.getInstance("BKS");
            InputStream cert = context().getAssets().open("cert");
            store.load(cert, "diningout".toCharArray());
            cert.close();
            TrustManagerFactory trust = TrustManagerFactory.getInstance("X509");
            trust.init(store);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trust.getTrustManagers(), new SecureRandom());
            Client client = new OkClient(
                    new OkHttpClient().setSslSocketFactory(ssl.getSocketFactory()));
            API = new Builder().setClient(client).setEndpoint(SERVER_URL)
                    .setRequestInterceptor(new Interceptor()).build().create(Api.class);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("loading server certificate", e);
        }
    }

    private Server() {
    }

    /**
     * Get initialisation data.
     *
     * @return null if there was a problem communicating with the server
     */
    public static Init init() {
        if (AuthToken.isAvailable()) {
            try {
                return API.init();
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Get restaurant details by global ID or Google ID.
     *
     * @return null if the restaurant does not exist or there was a problem communicating with the
     * server
     */
    public static Restaurant restaurant(Restaurant restaurant) {
        if (AuthToken.isAvailable()) {
            try {
                return API.restaurant(restaurant);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Get user and friend reviews for the restaurant by global ID or Google ID.
     *
     * @return null if the restaurant does not exist, the restaurant doesn't have any reviews, or
     * there was a problem communicating with the server
     */
    public static List<Review> reviews(Restaurant restaurant) {
        if (AuthToken.isAvailable()) {
            try {
                return API.reviews(restaurant);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Get reviews written by the user.
     *
     * @return null if the user has not written any reviews or there was a problem communicating
     * with the server
     */
    public static List<Review> reviews(User user) {
        if (AuthToken.isAvailable()) {
            try {
                return API.reviews(user);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Send contact changes and get global IDs for new contacts that are users.
     *
     * @return null if there was a problem communicating with the server
     */
    public static List<User> syncContacts(List<User> users) {
        if (AuthToken.isAvailable()) {
            try {
                return API.syncContacts(users);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Send restaurant changes and get global IDs for new restaurants.
     *
     * @return null if there was a problem communicating with the server
     */
    public static List<Restaurant> syncRestaurants(List<Restaurant> restaurants) {
        if (AuthToken.isAvailable()) {
            try {
                return API.syncRestaurants(restaurants);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Send review changes and get global IDs for new reviews.
     *
     * @return null if there was a problem communicating with the server
     */
    public static List<Review> syncReviews(List<Review> reviews) {
        if (AuthToken.isAvailable()) {
            try {
                return API.syncReviews(reviews);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Send review draft changes.
     *
     * @return null if there was a problem communicating with the server
     */
    public static List<Review> syncReviewDrafts(List<Review> drafts) {
        if (AuthToken.isAvailable()) {
            try {
                return API.syncReviewDrafts(drafts);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Get new remote changes.
     *
     * @return null if there was a problem communicating with the server
     */
    public static Syncing sync() {
        if (AuthToken.isAvailable()) {
            try {
                return API.sync();
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Send the cloud ID to the server.
     *
     * @return true if successful or null if there was a problem communicating with the server
     */
    public static Boolean syncCloudId(String id) {
        if (AuthToken.isAvailable()) {
            try {
                return API.syncCloudId(id);
            } catch (RetrofitError e) {
                log(e);
            }
        }
        return null;
    }

    /**
     * Log the error.
     */
    private static void log(RetrofitError e) {
        Log.e(TAG, "API call failed", e);
        exception(e);
    }

    /**
     * Server methods.
     */
    private interface Api {
        @GET("/init/v0")
        Init init();

        @POST("/restaurant/v0")
        Restaurant restaurant(@Body Restaurant restaurant);

        @POST("/restaurant-reviews/v0")
        List<Review> reviews(@Body Restaurant restaurant);

        @POST("/user-reviews/v0")
        List<Review> reviews(@Body User user);

        @POST("/sync-contacts/v0")
        List<User> syncContacts(@Body List<User> users);

        @POST("/sync-restaurants/v0")
        List<Restaurant> syncRestaurants(@Body List<Restaurant> restaurants);

        @POST("/sync-reviews/v0")
        List<Review> syncReviews(@Body List<Review> reviews);

        @POST("/sync-review-drafts/v0")
        List<Review> syncReviewDrafts(@Body List<Review> drafts);

        @GET("/sync/v0")
        Syncing sync();

        @POST("/sync-cloud-id/v0")
        boolean syncCloudId(@Body String id);
    }

    /**
     * Adds the current auth token and install ID to the request headers.
     */
    private static class Interceptor implements RequestInterceptor {
        @Override
        public void intercept(RequestFacade request) {
            request.addHeader("Authorization", AuthToken.get());
            request.addHeader("Install-ID",
                    String.valueOf(Prefs.getLong(context(), APP, INSTALL_ID)));
        }
    }
}
