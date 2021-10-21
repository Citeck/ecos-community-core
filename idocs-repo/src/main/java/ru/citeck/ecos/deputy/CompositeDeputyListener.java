/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.deputy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CompositeDeputyListener implements DeputyListener  {

	private List<DeputyListener> listeners = new ArrayList<>();

	public void addDeputyListener(DeputyListener listener) {
		listeners.add(listener);
		Collections.sort(listeners, (a, b) -> a.getPriority() - b.getPriority());
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@Override
	public void onRoleMemberAvailable(String roleFullName, String userName) {
		for (DeputyListener listener : listeners) {
			listener.onRoleMemberAvailable(roleFullName, userName);
		}
	}

	@Override
	public void onRoleMemberUnavailable(String roleFullName, String userName) {
		for (DeputyListener listener : listeners) {
			listener.onRoleMemberUnavailable(roleFullName, userName);
		}
	}

	@Override
	public void onRoleDeputyAvailable(String roleFullName, String userName) {
		for (DeputyListener listener : listeners) {
			listener.onRoleDeputyAvailable(roleFullName, userName);
		}
	}

	@Override
	public void onRoleDeputyUnavailable(String roleFullName, String userName) {
		for (DeputyListener listener : listeners) {
			listener.onRoleDeputyUnavailable(roleFullName, userName);
		}
	}

	@Override
	public void onUserDeputyAvailable(String userName, String deputyName) {
		for (DeputyListener listener : listeners) {
			listener.onUserDeputyAvailable(userName, deputyName);
		}
	}

	@Override
	public void onUserDeputyUnavailable(String userName, String deputyName) {
		for (DeputyListener listener : listeners) {
			listener.onUserDeputyUnavailable(userName, deputyName);
		}
	}

	@Override
	public void onUserAvailable(String userName) {
		for (DeputyListener listener : listeners) {
			listener.onUserAvailable(userName);
		}
	}

	@Override
	public void onUserUnavailable(String userName) {
		for (DeputyListener listener : listeners) {
			listener.onUserUnavailable(userName);
		}
	}

    @Override
    public void onAssistantAdded(String userName) {
        for (DeputyListener listener : listeners) {
            listener.onAssistantAdded(userName);
        }
    }

    @Override
	public void onAssistantRemoved(String userName, String deputyName) {
		for (DeputyListener listener : listeners) {
			listener.onAssistantRemoved(userName, deputyName);
		}
    }

    @Override
    public void onRoleAssistantAdded(String roleFullName, String assistantName) {
        for (DeputyListener listener : listeners) {
            listener.onRoleAssistantAdded(roleFullName, assistantName);
        }
    }

    @Override
    public void onRoleAssistantRemoved(String roleFullName, String assistantName) {
        for (DeputyListener listener : listeners) {
            listener.onRoleAssistantRemoved(roleFullName, assistantName);
        }
    }
}
