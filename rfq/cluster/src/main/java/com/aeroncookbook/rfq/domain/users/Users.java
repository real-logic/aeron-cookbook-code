package com.aeroncookbook.rfq.domain.users;

import org.agrona.collections.IntHashSet;

/**
 * A collection of hardcoded users.
 */
public class Users
{
    private final IntHashSet users = new IntHashSet();

    /**
     * Create a new collection of users.
     */
    public Users()
    {
        users.add(500);
        users.add(501);
        users.add(502);
    }

    public boolean isValidUser(final int userId)
    {
        return users.contains(userId);
    }
}
