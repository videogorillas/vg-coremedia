package com.vg;

import static java.lang.Math.max;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.stjs.javascript.Array;

public class ListUtils {
    @SafeVarargs
    public static <T> List<T> listConcat(Collection<T>... t) {
        if (t.length == 0) {
            return Collections.emptyList();
        }
        if (t.length == 1) {
            return list(t[0]);
        }
        List<T> list = new ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            list.addAll(t[i]);
        }
        return list;
    }

    @SafeVarargs
    public static <T> List<T> list(T... t) {
        return (t == null ? Collections.emptyList() : new ArrayList<>(Arrays.asList(t)));
    }

    public static <T> List<T> list(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    public static <T> List<T> list(Array<T> list) {
        return list == null ? Collections.emptyList() : list.toList();
    }

    public static <T> List<T> list(Iterable<T> iterable) {
        if (iterable instanceof List)
            return (List<T>) iterable;
        List<T> list = new ArrayList<>();
        for (T item : iterable) {
            list.add(item);
        }
        return list;
    }

    public static <E> List<E> list(Iterator<E> i) {
        List<E> list = new ArrayList<E>();
        while (i.hasNext()) {
            list.add(i.next());
        }
        return list;
    }

    public static <E> List<E> asList(Iterable<E> it) {
        if (it instanceof List<?>) {
            return (List<E>) it;
        } else if (it instanceof Collection<?>) {
            List<E> list = new ArrayList<E>();
            list.addAll((Collection<E>) it);
            return list;
        } else {
            List<E> list = new ArrayList<E>();
            for (E e : it) {
                list.add(e);
            }
            return list;
        }
    }

    public static <T> List<T> tail(List<T> list, int limit) {
        return list.isEmpty() ? list : list.subList(max(0, list.size() - 1 - limit), list.size() - 1);
    }

    public static <T> Array<T> array(List<T> collect) {
        Array<T> a = new Array<>();
        for (T t : collect) {
            a.push(t);
        }
        return a;
    }

    public static <T> T firstElement(Array<T> list) {
        if (list != null && list.$length() > 0) {
            return list.$get(0);
        }
        return null;
    }

    public static boolean isEmpty(Array<String> list) {
        return list == null || list.$length() == 0;
    }

}
