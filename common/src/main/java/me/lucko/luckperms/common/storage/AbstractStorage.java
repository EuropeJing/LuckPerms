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

package me.lucko.luckperms.common.storage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.google.common.base.Throwables;

import me.lucko.luckperms.api.HeldPermission;
import me.lucko.luckperms.api.LogEntry;
import me.lucko.luckperms.api.event.cause.CreationCause;
import me.lucko.luckperms.api.event.cause.DeletionCause;
import me.lucko.luckperms.common.actionlog.Log;
import me.lucko.luckperms.common.api.delegates.model.ApiStorage;
import me.lucko.luckperms.common.bulkupdate.BulkUpdate;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.storage.dao.AbstractDao;
import me.lucko.luckperms.common.storage.wrappings.BufferedOutputStorage;
import me.lucko.luckperms.common.storage.wrappings.PhasedStorage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Converts a {@link AbstractDao} to use {@link CompletableFuture}s
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AbstractStorage implements Storage {
    public static Storage create(LuckPermsPlugin plugin, AbstractDao backing) {
        BufferedOutputStorage bufferedDs = BufferedOutputStorage.wrap(PhasedStorage.wrap(new AbstractStorage(plugin, backing)), 250L);
        plugin.getScheduler().asyncRepeating(bufferedDs, 2L);
        return bufferedDs;
    }

    private final LuckPermsPlugin plugin;
    private final AbstractDao dao;

    @Getter
    private final ApiStorage delegate;

    private AbstractStorage(LuckPermsPlugin plugin, AbstractDao dao) {
        this.plugin = plugin;
        this.dao = dao;
        this.delegate = new ApiStorage(plugin, this);
    }

    private <T> CompletableFuture<T> makeFuture(Callable<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.call();
            } catch (Exception e) {
                Throwables.propagateIfPossible(e);
                throw new CompletionException(e);
            }
        }, dao.getPlugin().getScheduler().async());
    }

    @Override
    public String getName() {
        return dao.getName();
    }

    @Override
    public Storage noBuffer() {
        return this;
    }

    @Override
    public void init() {
        try {
            dao.init();
        } catch (Exception e) {
            plugin.getLog().severe("Failed to init storage dao");
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {
        try {
            dao.shutdown();
        } catch (Exception e) {
            plugin.getLog().severe("Failed to shutdown storage dao");
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, String> getMeta() {
        return dao.getMeta();
    }

    @Override
    public CompletableFuture<Boolean> logAction(LogEntry entry) {
        return makeFuture(() -> dao.logAction(entry));
    }

    @Override
    public CompletableFuture<Log> getLog() {
        return makeFuture(dao::getLog);
    }

    @Override
    public CompletableFuture<Boolean> applyBulkUpdate(BulkUpdate bulkUpdate) {
        return makeFuture(() -> dao.applyBulkUpdate(bulkUpdate));
    }

    @Override
    public CompletableFuture<Boolean> loadUser(UUID uuid, String username) {
        return makeFuture(() -> {
            if (dao.loadUser(uuid, username)) {
                User u = plugin.getUserManager().getIfLoaded(uuid);
                if (u != null) {
                    plugin.getApiProvider().getEventFactory().handleUserLoad(u);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> saveUser(User user) {
        return makeFuture(() -> dao.saveUser(user));
    }

    @Override
    public CompletableFuture<Set<UUID>> getUniqueUsers() {
        return makeFuture(dao::getUniqueUsers);
    }

    @Override
    public CompletableFuture<List<HeldPermission<UUID>>> getUsersWithPermission(String permission) {
        return makeFuture(() -> dao.getUsersWithPermission(permission));
    }

    @Override
    public CompletableFuture<Boolean> createAndLoadGroup(String name, CreationCause cause) {
        return makeFuture(() -> {
            if (dao.createAndLoadGroup(name)) {
                Group g = plugin.getGroupManager().getIfLoaded(name);
                if (g != null) {
                    plugin.getApiProvider().getEventFactory().handleGroupCreate(g, cause);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> loadGroup(String name) {
        return makeFuture(() -> {
            if (dao.loadGroup(name)) {
                Group g = plugin.getGroupManager().getIfLoaded(name);
                if (g != null) {
                    plugin.getApiProvider().getEventFactory().handleGroupLoad(g);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> loadAllGroups() {
        return makeFuture(() -> {
            if (dao.loadAllGroups()) {
                plugin.getApiProvider().getEventFactory().handleGroupLoadAll();
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> saveGroup(Group group) {
        return makeFuture(() -> dao.saveGroup(group));
    }

    @Override
    public CompletableFuture<Boolean> deleteGroup(Group group, DeletionCause cause) {
        return makeFuture(() -> {
            if (dao.deleteGroup(group)) {
                plugin.getApiProvider().getEventFactory().handleGroupDelete(group, cause);
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<List<HeldPermission<String>>> getGroupsWithPermission(String permission) {
        return makeFuture(() -> dao.getGroupsWithPermission(permission));
    }

    @Override
    public CompletableFuture<Boolean> createAndLoadTrack(String name, CreationCause cause) {
        return makeFuture(() -> {
            if (dao.createAndLoadTrack(name)) {
                Track t = plugin.getTrackManager().getIfLoaded(name);
                if (t != null) {
                    plugin.getApiProvider().getEventFactory().handleTrackCreate(t, cause);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> loadTrack(String name) {
        return makeFuture(() -> {
            if (dao.loadTrack(name)) {
                Track t = plugin.getTrackManager().getIfLoaded(name);
                if (t != null) {
                    plugin.getApiProvider().getEventFactory().handleTrackLoad(t);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> loadAllTracks() {
        return makeFuture(() -> {
            if (dao.loadAllTracks()) {
                plugin.getApiProvider().getEventFactory().handleTrackLoadAll();
                return true;
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> saveTrack(Track track) {
        return makeFuture(() -> dao.saveTrack(track));
    }

    @Override
    public CompletableFuture<Boolean> deleteTrack(Track track, DeletionCause cause) {
        return makeFuture(() -> {
            if (dao.deleteTrack(track)) {
                plugin.getApiProvider().getEventFactory().handleTrackDelete(track, cause);
                return true;
            }
            return false;
         });
    }

    @Override
    public CompletableFuture<Boolean> saveUUIDData(UUID uuid, String username) {
        return makeFuture(() -> dao.saveUUIDData(uuid, username));
    }

    @Override
    public CompletableFuture<UUID> getUUID(String username) {
        return makeFuture(() -> dao.getUUID(username));
    }

    @Override
    public CompletableFuture<String> getName(UUID uuid) {
        return makeFuture(() -> dao.getName(uuid));
    }
}
