package org.gnucash.android.db.adapter;

import static org.gnucash.android.db.DatabaseSchema.PriceEntry;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Price;
import org.gnucash.android.util.TimestampHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Database adapter for prices
 */
public class PricesDbAdapter extends DatabaseAdapter<Price> {
    @NonNull
    public final CommoditiesDbAdapter commoditiesDbAdapter;
    private final Map<String, Price> cachePair = new HashMap<>();

    /**
     * Opens the database adapter with an existing database
     *
     * @param commoditiesDbAdapter the commodities database adapter.
     */
    public PricesDbAdapter(@NonNull CommoditiesDbAdapter commoditiesDbAdapter) {
        super(commoditiesDbAdapter.holder, PriceEntry.TABLE_NAME, new String[]{
            PriceEntry.COLUMN_COMMODITY_UID,
            PriceEntry.COLUMN_CURRENCY_UID,
            PriceEntry.COLUMN_DATE,
            PriceEntry.COLUMN_SOURCE,
            PriceEntry.COLUMN_TYPE,
            PriceEntry.COLUMN_VALUE_NUM,
            PriceEntry.COLUMN_VALUE_DENOM
        }, true);
        this.commoditiesDbAdapter = commoditiesDbAdapter;
    }

    public static PricesDbAdapter getInstance() {
        return GnuCashApplication.getPricesDbAdapter();
    }

    @Override
    public void close() throws IOException {
        commoditiesDbAdapter.close();
        cachePair.clear();
        super.close();
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Price price) {
        bindBaseModel(stmt, price);
        stmt.bindString(1, price.getCommodityUID());
        stmt.bindString(2, price.getCurrencyUID());
        stmt.bindString(3, TimestampHelper.getUtcStringFromTimestamp(price.getDate()));
        if (price.getSource() != null) {
            stmt.bindString(4, price.getSource());
        }
        stmt.bindString(5, price.getType().getValue());
        stmt.bindLong(6, price.getValueNum());
        stmt.bindLong(7, price.getValueDenom());

        return stmt;
    }

    @Override
    public Price buildModelInstance(@NonNull final Cursor cursor) {
        String commodityUID = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_COMMODITY_UID));
        String currencyUID = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_CURRENCY_UID));
        String dateString = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_DATE));
        String source = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_SOURCE));
        String type = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_TYPE));
        long valueNum = cursor.getLong(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_VALUE_NUM));
        long valueDenom = cursor.getLong(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_VALUE_DENOM));

        Commodity commodity1 = commoditiesDbAdapter.getRecord(commodityUID);
        Commodity commodity2 = commoditiesDbAdapter.getRecord(currencyUID);
        Price price = new Price(commodity1, commodity2);
        populateBaseModelAttributes(cursor, price);
        price.setDate(TimestampHelper.getTimestampFromUtcString(dateString).getTime());
        price.setSource(source);
        price.setType(Price.Type.of(type));
        price.setValueNum(valueNum);
        price.setValueDenom(valueDenom);

        return price;
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     * @param commodityCode Currency code of the commodity which is starting point for conversion
     * @param currencyCode  Currency code of target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    public Price getPriceForCurrencies(@NonNull String commodityCode, @NonNull String currencyCode) {
        Commodity commodity = commoditiesDbAdapter.getCurrency(commodityCode);
        Commodity currency = commoditiesDbAdapter.getCurrency(currencyCode);
        return getPrice(commodity, currency);
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     * @param commodityUID GUID of the commodity which is starting point for conversion
     * @param currencyUID  GUID of target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    public Price getPrice(@NonNull String commodityUID, @NonNull String currencyUID) {
        Commodity commodity = commoditiesDbAdapter.getRecord(commodityUID);
        Commodity currency = commoditiesDbAdapter.getRecord(currencyUID);
        return getPrice(commodity, currency);
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     * @param commodity the commodity which is starting point for conversion
     * @param currency  the target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    @Nullable
    public Price getPrice(@NonNull Commodity commodity, @NonNull Commodity currency) {
        String commodityUID = commodity.getUID();
        String currencyUID = currency.getUID();
        String key = commodityUID + "/" + currencyUID;
        String keyInverse = currencyUID + "/" + commodityUID;
        if (isCached) {
            Price price = cachePair.get(key);
            if (price != null) return price;
            price = cachePair.get(keyInverse);
            if (price != null) return price;
        }
        if (commodity.equals(currency)) {
            Price price = new Price(commodity, currency, BigDecimal.ONE);
            if (isCached) {
                cachePair.put(key, price);
            }
            return price;
        }
        // the commodity and currency can be swapped
        String where = "(" + PriceEntry.COLUMN_COMMODITY_UID + " = ? AND " + PriceEntry.COLUMN_CURRENCY_UID + " = ?)"
            + " OR (" + PriceEntry.COLUMN_COMMODITY_UID + " = ? AND " + PriceEntry.COLUMN_CURRENCY_UID + " = ?)";
        String[] whereArgs = new String[]{commodityUID, currencyUID, currencyUID, commodityUID};
        // only get the latest price
        String orderBy = PriceEntry.COLUMN_DATE + " DESC";
        Cursor cursor = mDb.query(PriceEntry.TABLE_NAME, null, where, whereArgs, null, null, orderBy, "1");
        try {
            if (cursor.moveToFirst()) {
                Price price = buildModelInstance(cursor);
                long valueNum = price.getValueNum();
                long valueDenom = price.getValueDenom();
                if (valueNum <= 0 || valueDenom <= 0) {
                    // this should not happen
                    return null;
                }
                // swap numerator and denominator
                Price priceInverse = price.inverse();
                if (price.getCurrencyUID().equals(currencyUID)) {
                    if (isCached) {
                        cachePair.put(key, price);
                        cachePair.put(keyInverse, priceInverse);
                    }
                    return price;
                }
                if (isCached) {
                    cachePair.put(keyInverse, price);
                    cachePair.put(key, priceInverse);
                }
                return priceInverse;
            }
        } finally {
            cursor.close();
        }
        // TODO Try with intermediate currency, e.g. EUR -> ETB -> ILS
        return null;
    }

    @Override
    public void addRecord(@NonNull Price model, UpdateMethod updateMethod) throws SQLException {
        super.addRecord(model, updateMethod);
        if (isCached) {
            Commodity commodity = model.getCommodity();
            Commodity currency = model.getCurrency();
            String commodityUID = commodity.getUID();
            String currencyUID = currency.getUID();
            String key = commodityUID + "/" + currencyUID;
            String keyInverse = currencyUID + "/" + commodityUID;
            Price price = cachePair.get(key);
            if (price == null || price.getDate() < model.getDate()) {
                cachePair.put(key, model);
                cachePair.put(keyInverse, model.inverse());
            }
        }
    }
}
