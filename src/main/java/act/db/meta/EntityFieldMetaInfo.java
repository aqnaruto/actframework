package act.db.meta;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2018 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.osgl.$;
import org.osgl.util.E;

import java.util.Objects;

/**
 * Stores meta information about an Entity field
 */
public class EntityFieldMetaInfo {

    enum Trait {
        CREATED, LAST_MODIFIED, ID
    }

    private Trait trait;
    private String className;
    private String fieldName;
    private String columnName;
    private EntityClassMetaInfo classInfo;

    EntityFieldMetaInfo(EntityClassMetaInfo classInfo) {
        this.classInfo = $.notNull(classInfo);
    }

    public Trait trait() {
        return trait;
    }

    public void trait(Trait trait) {
        this.trait = $.notNull(trait);
        if (Trait.CREATED == trait) {
            classInfo.createdAtField(this);
        } else if (Trait.LAST_MODIFIED == trait) {
            classInfo.lastModifiedAtField(this);
        } else if (Trait.ID == trait) {
            classInfo.idField(this);
        } else {
            throw E.unexpected("oops");
        }
    }

    public boolean isCreatedAt() {
        return Trait.CREATED == trait;
    }

    public boolean isLastModifiedAt() {
        return Trait.LAST_MODIFIED == trait;
    }

    public String className() {
        return className;
    }

    public void className(String className) {
        this.className = className;
    }

    public String fieldName() {
        return fieldName;
    }

    public void fieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String columnName() {
        return columnName;
    }

    public void columnName(String columnName) {
        this.columnName = columnName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityFieldMetaInfo that = (EntityFieldMetaInfo) o;
        return trait == that.trait &&
                Objects.equals(className, that.className) &&
                Objects.equals(fieldName, that.fieldName) &&
                Objects.equals(columnName, that.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trait, className, fieldName, columnName);
    }

    @Override
    public String toString() {
        return "EntityFieldMetaInfo{" +
                "className='" + className + '\'' +
                ", fieldName='" + fieldName + '\'' +
                '}';
    }

}
