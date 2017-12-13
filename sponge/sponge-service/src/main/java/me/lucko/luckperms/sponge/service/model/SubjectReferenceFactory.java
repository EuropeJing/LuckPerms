/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.sponge.service.model;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Splitter;

import org.spongepowered.api.service.permission.Subject;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caches the creation of {@link SubjectReference}s.
 */
@UtilityClass
public final class SubjectReferenceFactory {

    /**
     * Cache based factory for SubjectReferences.
     *
     * Using a factory and caching here makes the Subject cache in SubjectReference
     * more effective. This reduces the no. of times i/o is executed due to resolve calls
     * within the SubjectReference.
     *
     * It's perfectly ok if two instances of the same SubjectReference exist. (hence the 1 hour expiry)
     */
    private static final LoadingCache<SubjectReferenceAttributes, SubjectReference> REFERENCE_CACHE = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(a -> new SubjectReference(a.permissionService, a.collectionId, a.id));

    @Deprecated
    public static SubjectReference deserialize(@NonNull LPPermissionService service, @NonNull String serialisedReference) {
        List<String> parts = Splitter.on('/').limit(2).splitToList(serialisedReference);
        return obtain(service, parts.get(0), parts.get(1));
    }

    public static SubjectReference obtain(@NonNull LPPermissionService service, @NonNull Subject subject) {
        return obtain(service, subject.getContainingCollection().getIdentifier(), subject.getIdentifier());
    }

    public static SubjectReference obtain(@NonNull LPPermissionService service, @NonNull org.spongepowered.api.service.permission.SubjectReference reference) {
        if (reference instanceof SubjectReference) {
            return ((SubjectReference) reference);
        } else {
            return obtain(service, reference.getCollectionIdentifier(), reference.getSubjectIdentifier());
        }
    }

    public static SubjectReference obtain(@NonNull LPPermissionService service, @NonNull String collectionIdentifier, @NonNull String subjectIdentifier) {
        return REFERENCE_CACHE.get(new SubjectReferenceAttributes(service, collectionIdentifier, subjectIdentifier));
    }

    /**
     * Used as a cache key.
     */
    @AllArgsConstructor
    private static final class SubjectReferenceAttributes {
        private final LPPermissionService permissionService;

        private final String collectionId;
        private final String id;

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof SubjectReferenceAttributes)) return false;
            final SubjectReferenceAttributes other = (SubjectReferenceAttributes) o;
            return this.collectionId.equals(other.collectionId) && this.id.equals(other.id);
        }

        @Override
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            result = result * PRIME + this.collectionId.hashCode();
            result = result * PRIME + this.id.hashCode();
            return result;
        }
    }

}
