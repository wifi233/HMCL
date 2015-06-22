/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils.tinystream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.jackhuang.hellominecraft.utils.functions.BiFunction;
import org.jackhuang.hellominecraft.utils.functions.Consumer;
import org.jackhuang.hellominecraft.utils.functions.Function;
import org.jackhuang.hellominecraft.utils.functions.Predicate;

/**
 *
 * @author huangyuhui
 */
public class Stream<T> {

    List<T> internal;

    public Stream(Collection<T> internal) {
        this.internal = new ArrayList<>(internal);
    }

    protected Stream() {
    }

    protected static <T> Stream<T> of(List<T> a) {
        Stream<T> b = new Stream<>();
        b.internal = a;
        return b;
    }

    public Stream<T> forEach(Consumer<? super T> p) {
        for (T t : internal) p.accept(t);
        return this;
    }

    public Stream<T> filter(Predicate<? super T> p) {
        ArrayList<T> newList = new ArrayList<>();
        forEach(a -> {
            if (p.apply(a)) newList.add(a);
        });
        internal = newList;
        return this;
    }

    public int count() {
        return internal.size();
    }

    public Stream<T> distinct() {
        internal = new ArrayList<>(new HashSet<>(internal));
        return this;
    }

    public <R> Stream<R> map(Function<? super T, ? extends R> func) {
        List<R> newList = new ArrayList<>(internal.size());
        forEach(a -> newList.add(func.apply(a)));
        return of(newList);
    }

    public Stream<T> sorted(Comparator<? super T> c) {
        Collections.sort(internal, c);
        return this;
    }

    public <U> U reduce(U identity, BiFunction<U, T, U> accumulator) {
        for (T t : internal) identity = accumulator.apply(identity, t);
        return identity;
    }

    public boolean anyMatch(Predicate<? super T> p) {
        return map(t -> p.apply(t)).reduce(false, (accumulator, _item) -> accumulator | _item);
    }

    public boolean allMatch(Predicate<? super T> p) {
        return map(t -> p.apply(t)).reduce(true, (accumulator, _item) -> accumulator & _item);
    }

    public T findFirst() {
        return internal.isEmpty() ? null : internal.get(0);
    }

}
