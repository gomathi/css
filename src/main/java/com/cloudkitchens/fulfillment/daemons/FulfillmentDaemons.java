package com.cloudkitchens.fulfillment.daemons;

import com.cloudkitchens.fulfillment.entities.Temperature;
import com.cloudkitchens.fulfillment.entities.orders.Order;
import com.cloudkitchens.fulfillment.entities.pickup.Dispatcher;
import com.cloudkitchens.fulfillment.entities.shelves.IShelfPod;
import com.cloudkitchens.fulfillment.entities.shelves.Shelf;
import com.cloudkitchens.fulfillment.entities.shelves.ShelfPod;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.PoissonDistribution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This class launches {@link IShelfPod} instance and {@link Dispatcher} as well. Shelves are setup based on the config provided,
 * otherwise default config is used for all shelves, look at daemons_config.json in the resources folder for the default config.
 * <p>
 * Reads the orders json file(file name is passed as the parameter), and inserts the orders into {@link IShelfPod}, and dispatcher
 * sends messages for pickup. The orders are inserted based on poisson distribution. When all the orders are inserted,
 * after specific delay the daemons stops all the background threads and exits.
 */
@Slf4j public class FulfillmentDaemons {

    private static final Map<String, Temperature> STR_VALUES_TO_TEMPERATURE =
        ImmutableMap.of("hot", Temperature.Hot, "cold", Temperature.Cold, "frozen", Temperature.Frozen, "overflow", Temperature.Overflow);

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        URL configResourceURL = FulfillmentDaemons.class.getClassLoader().getResource("daemons_config.json");
        File configFile = Paths.get(configResourceURL.toURI()).toFile();
        String configFileAbsolutePath = configFile.getAbsolutePath();

        launchFulfillmentDaemons(configFileAbsolutePath, "/workplace/projects/css-data/Engineering_Challenge_-_Orders.json");
    }

    public static void launchFulfillmentDaemons(String daemonsConfigFilePath, String ordersFilePath)
        throws FileNotFoundException, InterruptedException {
        Config config = createConfig(daemonsConfigFilePath);

        log.info("Configurations ");

        List<Shelf> shelves = createShelves(config.getShelfInputs());
        for (Shelf shelf : shelves) {
            log.info("Available shelf={}", shelf);
        }
        ShelfPod shelfPod = new ShelfPod(shelves);
        Dispatcher dispatcher = new Dispatcher(shelfPod, config.getMinDelayForPickupInSecs(), config.getMaxDelayForPickupInSecs());

        dispatcher.startBackgroundActivities();
        shelfPod.startBackgroundActivities();

        List<OrderInput> orderInputs = getOrders(ordersFilePath);
        Iterator<OrderInput> inputItr = orderInputs.iterator();
        addOrdersToShelfPodUsingWithPoissonDistribution(config, inputItr, shelfPod, dispatcher);
        System.exit(1);
    }

    private static void addOrdersToShelfPodUsingWithPoissonDistribution(Config config, Iterator<OrderInput> inputItr, ShelfPod shelfPod,
        Dispatcher dispatcher) throws InterruptedException {
        PoissonDistribution pd = new PoissonDistribution(config.getPoissonMeanPerSecond());
        List<Order> orders = new ArrayList<>();
        while (true) {
            int samples = pd.sample();

            for (int i = 0; i < samples && inputItr.hasNext(); i++) {
                OrderInput orderInput = inputItr.next();
                Order order =
                    new Order(UUID.randomUUID().toString(), orderInput.getName(), STR_VALUES_TO_TEMPERATURE.get(orderInput.getTemp()),
                        orderInput.getShelfLife(), orderInput.getDecayRate());
                orders.add(order);
                shelfPod.addOrder(order);
            }

            printOrdersInTheShelf(shelfPod);
            // We are trying to achieve poisson mean per second. So lets sleep for a second, and then proceed
            // with the next batch's insertion into the shelf.
            Thread.sleep(1000);

            if (!inputItr.hasNext()) {
                // When the last batch of orders are inserted, they will be picked up only after getMaxDelayForPickupInSecs(in the worst case).
                // So lets sleep for that time
                Thread.sleep(config.getMaxDelayForPickupInSecs() * 1000 + 2000);
                dispatcher.stopBackgroundActivities();
                shelfPod.stopBackgroundActivities();

                printOrdersInTheShelf(shelfPod);
                logOrders(orders);
                return;
            }
        }
    }

    /**
     * Lets log the orders and the corresponding state. So we can stats like delivered and expired.
     *
     * @param orders
     */
    private static void logOrders(List<Order> orders) {
        for (Order order : orders)
            log.info("orderInfo={}", order);
    }

    /**
     * Prints the orders in the shelf after every batch of orders insertion.
     *
     * @param shelfPod
     */
    private static void printOrdersInTheShelf(IShelfPod shelfPod) {
        log.info("******************************************************************");
        log.info("Current orders in the ShelfPod.");
        log.info("");
        for (Order order : shelfPod.getOrders()) {
            log.info("orderInTheShelf={}", order);
        }
        log.info("******************************************************************");
        log.info("");
    }

    /**
     * Reads config json, and builds POJO with various values including poissonMeanPerSecond,
     * minDelayForPickupInSecs, maxDelayForPickupInSecs and shelf configurations.
     *
     * @param configFilePath
     * @return
     * @throws FileNotFoundException
     */
    private static Config createConfig(String configFilePath) throws FileNotFoundException {
        Config.ConfigBuilder configBuilder = Config.builder();
        JsonParser parser = new JsonParser();
        BufferedReader br = new BufferedReader(new FileReader(configFilePath));
        JsonObject configJson = parser.parse(br).getAsJsonObject();

        double poissonMeanPerSecond = configJson.get("poissonMeanPerSecond").getAsDouble();
        configBuilder.poissonMeanPerSecond(poissonMeanPerSecond);

        int minDelayForPickupInSecs = configJson.get("minDelayForPickupInSecs").getAsInt();
        configBuilder.minDelayForPickupInSecs(minDelayForPickupInSecs);

        int maxDelayForPickupInSecs = configJson.get("maxDelayForPickupInSecs").getAsInt();
        configBuilder.maxDelayForPickupInSecs(maxDelayForPickupInSecs);

        Gson gson = new Gson();
        Type type = new TypeToken<List<ShelfInput>>() {
        }.getType();
        List<ShelfInput> shelfInputs = gson.fromJson(configJson.getAsJsonArray("shelves"), type);
        configBuilder.shelfInputs(shelfInputs);

        return configBuilder.build();
    }

    /**
     * Creates {@link Shelf} objects based on the given shelfInputs.
     *
     * @return
     */
    private static List<Shelf> createShelves(List<ShelfInput> shelfInputs) {
        List<Shelf> shelves = new ArrayList<>();
        for (ShelfInput shelfInput : shelfInputs) {
            shelves.add(new Shelf(UUID.randomUUID().toString(), shelfInput.getDecayRateFactor(), shelfInput.getCapacity(),
                STR_VALUES_TO_TEMPERATURE.get(shelfInput.getTemperature())));
        }
        return shelves;
    }

    /**
     * Given orders file, converts into OrderInput instances.
     *
     * @param filePath
     * @return
     * @throws FileNotFoundException
     */
    private static List<OrderInput> getOrders(String filePath) throws FileNotFoundException {
        Gson gson = new Gson();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        Type type = new TypeToken<List<OrderInput>>() {
        }.getType();
        List<OrderInput> orders = gson.fromJson(br, type);
        return orders;
    }
}
