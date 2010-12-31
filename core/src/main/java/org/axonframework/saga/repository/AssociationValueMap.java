/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga.repository;

import org.axonframework.saga.AssociationValue;

import java.util.Comparator;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.springframework.util.ObjectUtils.nullSafeEquals;

/**
 * In-memory storage for AssociationValue to Saga mappings. A single AssociationValue can map to several Sagas, and a
 * single Saga can be mapped by several AssociationValues.
 * <p/>
 * Note that this "map" does not implement the Map interface. This is mainly due to the specific nature and intent of
 * this implementation. For example, the Map interface does not allow a single key to point to more than one value.
 * <p/>
 * This implementation is thread safe and has an expected average time cost of <code>log(n)</code>.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AssociationValueMap {

    private final NavigableSet<SagaAssociationValue> mappings;

    /**
     * Initializes a new and empty AssociationValueMap.
     */
    public AssociationValueMap() {
        mappings = new ConcurrentSkipListSet<SagaAssociationValue>(new AssociationValueComparator());
    }

    /**
     * Returns the identifiers of the Sagas that have been associated with the given <code>associationValue</code>.
     *
     * @param associationValue The associationValue to find Sagas for
     * @return A set of Saga identifiers
     */
    public Set<String> findSagas(AssociationValue associationValue) {
        Set<String> identifiers = new HashSet<String>();
        for (SagaAssociationValue item : mappings.tailSet(new SagaAssociationValue(associationValue, null))) {
            if (!item.getKey().equals(associationValue.getKey())) {
                // we've had all relevant items
                break;
            }
            if (associationValue.equals(item.getAssociationValue())) {
                identifiers.add(item.getSagaIdentifier());
            }
        }
        return identifiers;
    }

    /**
     * Adds an association between the given <code>associationValue</code> and <code>sagaIdentifier</code>.
     *
     * @param associationValue The association value associated with the Saga
     * @param sagaIdentifier   The identifier of the associated Saga
     */
    public void add(AssociationValue associationValue, String sagaIdentifier) {
        mappings.add(new SagaAssociationValue(associationValue, sagaIdentifier));
    }

    /**
     * Removes an association between the given <code>associationValue</code> and <code>sagaIdentifier</code>.
     *
     * @param associationValue The association value associated with the Saga
     * @param sagaIdentifier   The identifier of the associated Saga
     */
    public void remove(AssociationValue associationValue, String sagaIdentifier) {
        mappings.remove(new SagaAssociationValue(associationValue, sagaIdentifier));
    }

    /**
     * Clears all the associations.
     */
    public void clear() {
        mappings.clear();
    }

    private static class SagaAssociationValue {

        private final AssociationValue associationValue;
        private final String sagaIdentifier;

        private SagaAssociationValue(AssociationValue associationValue, String sagaIdentifier) {
            this.associationValue = associationValue;
            this.sagaIdentifier = sagaIdentifier;
        }

        public AssociationValue getAssociationValue() {
            return associationValue;
        }

        public String getSagaIdentifier() {
            return sagaIdentifier;
        }

        public String getKey() {
            return associationValue.getKey();
        }

        public Object getValue() {
            return associationValue.getValue();
        }
    }

    /**
     * Indicates whether any elements are contained within this map.
     *
     * @return <code>true</code> if this Map is empty, <code>false</code> if it contains any associations.
     */
    public boolean isEmpty() {
        return mappings.isEmpty();
    }

    /**
     * Returns an approximation of the size of this map. Due to the concurrent nature of this map, size cannot return an
     * accurate value.
     * <p/>
     * This is not a constant-time operation. The backing store of this map requires full traversal of elements to
     * calculate this size.
     *
     * @return an approximation of the number of elements in this map
     */
    public int size() {
        return mappings.size();
    }

    private static class AssociationValueComparator implements Comparator<SagaAssociationValue> {

        @SuppressWarnings({"unchecked"})
        @Override
        public int compare(SagaAssociationValue o1, SagaAssociationValue o2) {
            int value = o1.getKey().compareTo(o2.getKey());
            if (value == 0 && !nullSafeEquals(o1.getValue(), o2.getValue())) {
                value = o1.getValue().getClass().getName().compareTo(o2.getValue().getClass().getName());
            }
            if (value == 0 && !nullSafeEquals(o1.getValue(), o2.getValue())) {
                // the objects are of the same class
                if (o1.getValue() instanceof Comparable) {
                    value = ((Comparable) o1.getValue()).compareTo(o2.getValue());
                } else {
                    value = o1.getValue().hashCode() - o2.getValue().hashCode();
                    if (value == 0 && o1.getValue() != o2.getValue()) {
                        value = o1.getValue().toString().compareTo(o2.getValue().toString());
                    }
                }
            }

            if (value == 0 && !nullSafeEquals(o1.getSagaIdentifier(), o2.getSagaIdentifier())) {
                if (o1.getSagaIdentifier() == null) {
                    return -1;
                } else if (o2.getSagaIdentifier() == null) {
                    return 1;
                }
                return o1.getSagaIdentifier().compareTo(o2.getSagaIdentifier());
            }
            return value;
        }
    }
}
